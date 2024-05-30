// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.json.JsonBundle
import com.intellij.json.psi.*
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.openapi.editor.actionSystem.CaretSpecificDataContext
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.IconManager.Companion.getInstance
import com.intellij.ui.PlatformIcons
import com.intellij.util.ObjectUtils
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonSchemaCompletionHandlerProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaNestedCompletionsTreeProvider.Companion.getNestedCompletionsData
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_ENUM_ORDER_SENSITIVE
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_LANGUAGE_INJECTION
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils
import com.jetbrains.jsonSchema.impl.nestedCompletions.*
import one.util.streamex.StreamEx
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
import javax.swing.Icon

private const val BUILTIN_USAGE_KEY = "builtin"
private const val SCHEMA_USAGE_KEY = "schema"
private const val USER_USAGE_KEY = "user"
private const val REMOTE_USAGE_KEY = "remote"

class JsonSchemaCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val file = PsiUtilCore.getVirtualFile(position)
    if (file == null) return

    val service = JsonSchemaService.Impl.get(position.project)
    if (!service.isApplicableToFile(file)) return
    val rootSchema = service.getSchemaObject(position.containingFile)
    if (rootSchema == null) return
    val positionParent = position.parent
    if (positionParent != null) {
      val parent = positionParent.parent
      if (parent is JsonProperty) {
        val propName = parent.name
        if (("\$schema" == propName && parent.parent is JsonObject && parent.parent.parent is JsonFile
             || "\$ref" == propName && service.isSchemaFile(file))
        ) {
          return
        }
      }
    }

    updateStat(service.getSchemaProvider(rootSchema), service.resolveSchemaFile(rootSchema))
    doCompletion(parameters, result, rootSchema, true)
  }

  private class Worker(private val myRootSchema: JsonSchemaObject,
                       private val myPosition: PsiElement,
                       private val myOriginalPosition: PsiElement,
                       private val myResultConsumer: Consumer<LookupElement>) {
    private val myWrapInQuotes: Boolean
    private val myInsideStringLiteral: Boolean

    // we need this set to filter same-named suggestions (they can be suggested by several matching schemes)
    val myVariants: MutableSet<LookupElement> = HashSet()
    private val myWalker: JsonLikePsiWalker?
    private val myProject: Project = myOriginalPosition.project

    fun work() {
      if (myWalker == null) return
      val checkable = myWalker.findElementToCheck(myPosition)
      if (checkable == null) return
      val isName = myWalker.isName(checkable)
      val position = myWalker.findPosition(checkable, isName == ThreeState.NO)
      if (position == null || position.isEmpty && isName == ThreeState.NO) return

      val knownNames: MutableSet<String> = HashSet()

      val nestedCompletionsNode = getNestedCompletionsData(myOriginalPosition.containingFile)
        .navigate(position
        )

      JsonSchemaResolver(myProject, myRootSchema, position)
        .resolve()
        .forEach(java.util.function.Consumer { schema: JsonSchemaObject ->
          schema.collectNestedCompletions(myProject, nestedCompletionsNode, null) { path: SchemaPath?, subSchema: JsonSchemaObject ->
            processSchema(subSchema, isName, checkable, knownNames, path)
            Unit
          }
        })

      for (variant in myVariants) {
        myResultConsumer.accept(variant)
      }
    }

    /**
     * @param completionPath Linked node representation of the names of all the parent
     * schema objects that we have navigated for nested completions
     */
    fun processSchema(schema: JsonSchemaObject,
                      isName: ThreeState,
                      checkable: PsiElement,
                      knownNames: MutableSet<String>,
                      completionPath: SchemaPath?) {
      if (isName != ThreeState.NO) {
        val completionOriginalPosition = myWalker!!.findChildBy(completionPath, myOriginalPosition)
        val completionPosition = myWalker.findChildBy(completionPath, myPosition)
        val insertComma = myWalker.hasMissingCommaAfter(myPosition)
        val hasValue = myWalker.isPropertyWithValue(checkable)

        val properties = myWalker.getPropertyNamesOfParentObject(completionOriginalPosition, completionPosition)
        val adapter = myWalker.getParentPropertyAdapter(completionOriginalPosition)

        val forbiddenNames = findPropertiesThatMustNotBePresent(schema, myPosition, myProject, properties)
          .plus(properties
          )
        addAllPropertyVariants(schema, insertComma, hasValue, forbiddenNames, adapter, knownNames, completionPath)
        addIfThenElsePropertyNameVariants(schema, insertComma, hasValue, forbiddenNames, adapter, knownNames, completionPath)
        addPropertyNameSchemaVariants(schema)
      }

      if (isName != ThreeState.YES) {
        suggestValues(schema, isName == ThreeState.NO, completionPath)
      }
    }

    fun addPropertyNameSchemaVariants(schema: JsonSchemaObject) {
      val propertyNamesSchema = schema.propertyNamesSchema
      if (propertyNamesSchema == null) return
      val anEnum = propertyNamesSchema.enum
      if (anEnum == null) return
      for (o in anEnum) {
        if (o !is String) continue
        myVariants.add(LookupElementBuilder.create(StringUtil.unquoteString(
          if (!shouldWrapInQuotes(o, false)) o else StringUtil.wrapWithDoubleQuote(o)
        )))
      }
    }

    fun addIfThenElsePropertyNameVariants(schema: JsonSchemaObject,
                                          insertComma: Boolean,
                                          hasValue: Boolean,
                                          forbiddenNames: Set<String>,
                                          adapter: JsonPropertyAdapter?,
                                          knownNames: MutableSet<String>,
                                          completionPath: SchemaPath?) {
      val ifThenElseList = schema.ifThenElse
      if (ifThenElseList == null) return

      val walker = JsonLikePsiWalker.getWalker(myPosition, schema)
      val propertyAdapter = walker?.getParentPropertyAdapter(myPosition)
      if (propertyAdapter == null) return

      val `object` = propertyAdapter.parentObject
      if (`object` == null) return

      for (ifThenElse in ifThenElseList) {
        val effectiveBranch = ifThenElse.effectiveBranchOrNull(myProject, `object`)
        if (effectiveBranch == null) continue

        addAllPropertyVariants(effectiveBranch, insertComma, hasValue, forbiddenNames, adapter, knownNames, completionPath
        )
      }
    }

    fun addAllPropertyVariants(schema: JsonSchemaObject,
                               insertComma: Boolean,
                               hasValue: Boolean,
                               forbiddenNames: Set<String>,
                               adapter: JsonPropertyAdapter?,
                               knownNames: MutableSet<String>,
                               completionPath: SchemaPath?) {
      StreamEx.of(schema.propertyNames)
        .filter { name: String -> !forbiddenNames.contains(name) && !knownNames.contains(name) || adapter != null && name == adapter.name }
        .forEach { name: String ->
          knownNames.add(name)
          val propertySchema = checkNotNull(schema.getPropertyByName(name))
          addPropertyVariant(name, propertySchema, hasValue, insertComma, completionPath)
        }
    }

    init {
      val psiWalker = JsonLikePsiWalker.getWalker(myPosition, myRootSchema)
      val positionParent = myPosition.parent
      val isInsideQuotedString = (positionParent != null && psiWalker != null && psiWalker.isQuotedString(positionParent))
      myWrapInQuotes = !isInsideQuotedString
      myWalker = psiWalker
      myInsideStringLiteral = isInsideQuotedString
    }

    fun suggestValues(schema: JsonSchemaObject, isSurelyValue: Boolean, completionPath: SchemaPath?) {
      suggestValuesForSchemaVariants(schema.anyOf, isSurelyValue, completionPath)
      suggestValuesForSchemaVariants(schema.oneOf, isSurelyValue, completionPath)
      suggestValuesForSchemaVariants(schema.allOf, isSurelyValue, completionPath)

      if (schema.enum != null && completionPath == null) {
        // custom insert handlers are currently applicable only to enum values but can be extended later to cover more cases
        val customHandlers = JsonSchemaCompletionHandlerProvider.EXTENSION_POINT_NAME.extensionList
        val metadata = schema.enumMetadata
        val isEnumOrderSensitive = schema.readChildNodeValue(X_INTELLIJ_ENUM_ORDER_SENSITIVE).toBoolean()
        val anEnum = schema.enum
        for (i in anEnum!!.indices) {
          val o = anEnum[i]
          if (myInsideStringLiteral && o !is String) continue
          val variant = o.toString()
          if (!filtered.contains(variant)) {
            val valueMetadata = metadata?.get(StringUtil.unquoteString(variant))
            val description = valueMetadata?.get("description")
            val deprecated = valueMetadata?.get("deprecationMessage")
            val order = if (isEnumOrderSensitive) i else null
            val handlers = customHandlers.mapNotNull { p -> p.createHandlerForEnumValue(schema, variant) }.toList()
            addValueVariant(variant, description, if (deprecated != null) ("$variant ($deprecated)") else null,
                            if (handlers.size == 1) handlers[0] else null, order)
          }
        }
      }
      else if (isSurelyValue) {
        val type = JsonSchemaObjectReadingUtils.guessType(schema)
        suggestSpecialValues(type)
        if (type != null) {
          suggestByType(schema, type)
        }
        else if (schema.typeVariants != null) {
          for (schemaType in schema.typeVariants!!) {
            suggestByType(schema, schemaType)
          }
        }
      }
    }

    fun suggestSpecialValues(type: JsonSchemaType?) {
      if (JsonSchemaVersion.isSchemaSchemaId(myRootSchema.id) && type == JsonSchemaType._string) {
        val propertyAdapter = myWalker!!.getParentPropertyAdapter(myOriginalPosition)
        if (propertyAdapter == null) {
          return
        }
        val name = propertyAdapter.name
        if (name == null) {
          return
        }
        when (name) {
          "required" -> addRequiredPropVariants()
          X_INTELLIJ_LANGUAGE_INJECTION -> addInjectedLanguageVariants()
          "language" -> {
            val parent = propertyAdapter.parentObject
            if (parent != null) {
              val adapter = myWalker.getParentPropertyAdapter(parent.delegate)
              if (adapter != null && X_INTELLIJ_LANGUAGE_INJECTION == adapter.name) {
                addInjectedLanguageVariants()
              }
            }
          }
        }
      }
    }

    fun addInjectedLanguageVariants() {
      val checkable = myWalker!!.findElementToCheck(myPosition)
      if (checkable !is JsonStringLiteral && checkable !is JsonReferenceExpression) return
      JBIterable.from(Language.getRegisteredLanguages())
        .filter { language: Language? ->
          LanguageUtil.isInjectableLanguage(
            language!!)
        }
        .map { language: Language? -> Injectable.fromLanguage(language) }
        .forEach(
          java.util.function.Consumer { it: Injectable ->
            myVariants.add(LookupElementBuilder
                             .create(it.id)
                             .withIcon(it.icon)
                             .withTailText("(" + it.displayName + ")", true))
          })
    }

    fun addRequiredPropVariants() {
      val checkable = myWalker!!.findElementToCheck(myPosition)
      if (checkable !is JsonStringLiteral && checkable !is JsonReferenceExpression) return
      val propertiesObject = JsonRequiredPropsReferenceProvider.findPropertiesObject(checkable)
      if (propertiesObject == null) return
      val parent = checkable.parent
      val items = if (parent is JsonArray
      ) parent.valueList
        .filterIsInstance<JsonStringLiteral>().map { it.value }.toSet()
      else HashSet()
      propertiesObject.propertyList.map { it.name }.filter {!items.contains(it) }
        .forEach { addStringVariant(it) }
    }

    fun suggestByType(schema: JsonSchemaObject, type: JsonSchemaType) {
      if (JsonSchemaType._string == type) {
        addPossibleStringValue(schema)
      }
      if (myInsideStringLiteral) {
        return
      }
      if (JsonSchemaType._boolean == type) {
        addPossibleBooleanValue(type)
      }
      else if (JsonSchemaType._null == type) {
        addValueVariant("null", null)
      }
      else if (JsonSchemaType._array == type) {
        val value = myWalker!!.defaultArrayValue
        addValueVariant(value, null,
                        "[...]", createArrayOrObjectLiteralInsertHandler(
            myWalker.hasWhitespaceDelimitedCodeBlocks(), value.length)
        )
      }
      else if (JsonSchemaType._object == type) {
        val value = myWalker!!.defaultObjectValue
        addValueVariant(value, null,
                        "{...}", createArrayOrObjectLiteralInsertHandler(
            myWalker.hasWhitespaceDelimitedCodeBlocks(), value.length)
        )
      }
    }

    fun addPossibleStringValue(schema: JsonSchemaObject) {
      val defaultValue = schema.default
      val defaultValueString = defaultValue?.toString()
      addStringVariant(defaultValueString)
    }

    fun addStringVariant(defaultValueString: String?) {
      if (defaultValueString != null) {
        var normalizedValue: String = defaultValueString
        val shouldQuote = myWalker!!.requiresValueQuotes()
        val isQuoted = StringUtil.isQuotedString(normalizedValue)
        if (shouldQuote && !isQuoted) {
          normalizedValue = StringUtil.wrapWithDoubleQuote(normalizedValue)
        }
        else if (!shouldQuote && isQuoted) {
          normalizedValue = StringUtil.unquoteString(normalizedValue)
        }
        addValueVariant(normalizedValue, null)
      }
    }

    fun suggestValuesForSchemaVariants(list: List<JsonSchemaObject>?, isSurelyValue: Boolean, completionPath: SchemaPath?) {
      if (!list.isNullOrEmpty()) {
        for (schemaObject in list) {
          suggestValues(schemaObject, isSurelyValue, completionPath)
        }
      }
    }

    fun addPossibleBooleanValue(type: JsonSchemaType) {
      if (JsonSchemaType._boolean == type) {
        addValueVariant("true", null)
        addValueVariant("false", null)
      }
    }


    fun addValueVariant(key: String,
                        description: String?,
                        altText: String? = null,
                        handler: InsertHandler<LookupElement?>? = null,
                        order: Int? = null) {
      val unquoted = StringUtil.unquoteString(key)
      var builder = LookupElementBuilder.create(if (!shouldWrapInQuotes(unquoted, true)) unquoted else key)
      if (altText != null) {
        builder = builder.withPresentableText(altText)
      }
      if (description != null) {
        builder = builder.withTypeText(description)
      }
      if (handler != null) {
        builder = builder.withInsertHandler(handler)
      }
      if (order != null) {
        myVariants.add(PrioritizedLookupElement.withPriority(builder, -order.toDouble()))
      }
      else {
        myVariants.add(builder)
      }
    }

    fun shouldWrapInQuotes(key: String?, isValue: Boolean): Boolean {
      return myWrapInQuotes && myWalker != null &&
             (isValue && myWalker.requiresValueQuotes() || !isValue && myWalker.requiresNameQuotes() || !myWalker.isValidIdentifier(key,
                                                                                                                                    myProject))
    }

    fun addPropertyVariant(key: String,
                           jsonSchemaObject: JsonSchemaObject,
                           hasValue: Boolean,
                           insertComma: Boolean,
                           completionPath: SchemaPath?) {
      var propertyKey = key
      var schemaObject = jsonSchemaObject
      val variants = JsonSchemaResolver(myProject, schemaObject).resolve()
      schemaObject = ObjectUtils.coalesce(variants.firstOrNull(), schemaObject)
      propertyKey = if (!shouldWrapInQuotes(propertyKey, false)) propertyKey else StringUtil.wrapWithDoubleQuote(propertyKey)
      var builder = LookupElementBuilder.create(propertyKey)

      val typeText = JsonSchemaDocumentationProvider.getBestDocumentation(true, schemaObject)
      if (!typeText.isNullOrBlank()) {
        val text = StringUtil.removeHtmlTags(typeText)
        builder = builder.withTypeText(findFirstSentence(text), true)
      }
      else {
        val type = JsonSchemaObjectReadingUtils.getTypeDescription(schemaObject, true)
        if (type != null) {
          builder = builder.withTypeText(type, true)
        }
      }

      builder = builder.withIcon(getIcon(JsonSchemaObjectReadingUtils.guessType(schemaObject)))

      if (hasSameType(variants)) {
        val type = JsonSchemaObjectReadingUtils.guessType(schemaObject)
        val values = schemaObject.enum
        val defaultValue = schemaObject.default

        builder = if (type != null || !values.isNullOrEmpty() || defaultValue != null) {
          builder.withInsertHandler(
            if (values.isNullOrEmpty() || values.map { v -> v.javaClass }.distinct().count() == 1) createPropertyInsertHandler(
              schemaObject, hasValue, insertComma)
            else createDefaultPropertyInsertHandler(true, insertComma))
        }
        else {
          builder.withInsertHandler(createDefaultPropertyInsertHandler(hasValue, insertComma))
        }
      }
      else {
        builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(hasValue, insertComma))
      }

      val deprecationMessage = schemaObject.deprecationMessage
      if (deprecationMessage != null) {
        builder = builder.withTailText(JsonBundle.message("schema.documentation.deprecated.postfix"), true).withStrikeoutness(true)
      }

      myVariants.add(builder.prefixedBy(completionPath, myWalker!!))
    }

    fun createDefaultPropertyInsertHandler(hasValue: Boolean,
                                           insertComma: Boolean): InsertHandler<LookupElement> {
      return object : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
          ApplicationManager.getApplication().assertWriteAccessAllowed()
          val editor = context.editor
          val project = context.project

          if (handleInsideQuotesInsertion(context, editor, hasValue, myInsideStringLiteral)) return
          var offset = editor.caretModel.offset
          val initialOffset = offset
          val docChars = context.document.charsSequence
          while (offset < docChars.length && Character.isWhitespace(docChars[offset])) {
            offset++
          }
          val propertyValueSeparator = myWalker!!.getPropertyValueSeparator(null)
          if (hasValue) {
            // fix colon for YAML and alike
            if (offset < docChars.length && !isSeparatorAtOffset(docChars, offset, propertyValueSeparator)) {
              editor.document.insertString(initialOffset, propertyValueSeparator)
              handleWhitespaceAfterColon(editor, docChars, initialOffset + propertyValueSeparator.length)
            }
            return
          }

          if (offset < docChars.length && isSeparatorAtOffset(docChars, offset, propertyValueSeparator)) {
            handleWhitespaceAfterColon(editor, docChars, offset + propertyValueSeparator.length)
          }
          else {
            // inserting longer string for proper formatting
            val stringToInsert = propertyValueSeparator + " 1" + (if (insertComma) "," else "")
            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, propertyValueSeparator.length + 1)
            formatInsertedString(context, stringToInsert.length)
            offset = editor.caretModel.offset
            context.document.deleteString(offset, offset + 1)
          }
          PsiDocumentManager.getInstance(project).commitDocument(editor.document)
          AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
        }

        fun isSeparatorAtOffset(docChars: CharSequence, offset: Int, propertyValueSeparator: String): Boolean {
          return docChars.subSequence(offset, docChars.length).toString().startsWith(propertyValueSeparator)
        }

        fun handleWhitespaceAfterColon(editor: Editor, docChars: CharSequence, nextOffset: Int) {
          if (nextOffset < docChars.length && docChars[nextOffset] == ' ') {
            editor.caretModel.moveToOffset(nextOffset + 1)
          }
          else {
            editor.caretModel.moveToOffset(nextOffset)
            EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1)
          }
        }
      }
    }

    fun createPropertyInsertHandler(jsonSchemaObject: JsonSchemaObject,
                                    hasValue: Boolean,
                                    insertComma: Boolean): InsertHandler<LookupElement> {
      var type = JsonSchemaObjectReadingUtils.guessType(jsonSchemaObject)
      val values = jsonSchemaObject.enum
      if (type == null && !values.isNullOrEmpty()) type = detectType(values)
      val defaultValue = jsonSchemaObject.default
      val defaultValueAsString = if (defaultValue == null || defaultValue is JsonSchemaObject) null else (if (defaultValue is String) "\"" + defaultValue + "\"" else defaultValue.toString())
      val finalType = type
      return createPropertyInsertHandler(hasValue, insertComma, finalType, defaultValueAsString, values, myWalker!!, myInsideStringLiteral)
    }

    companion object {
      // some schemas provide an empty array or an empty object in enum values...
      private val filtered = setOf("[]", "{}", "[ ]", "{ }")

      private fun findFirstSentence(sentence: String): String {
        var i = sentence.indexOf(". ")
        while (i >= 0) {
          val egText = ", e.g."
          if (!sentence.regionMatches(i - egText.length + 1, egText, 0, egText.length)) {
            return sentence.substring(0, i + 1)
          }
          i = sentence.indexOf(". ", i + 1)
        }
        return sentence
      }

      private fun getIcon(type: JsonSchemaType?): Icon {
        if (type == null) {
          return getInstance().getPlatformIcon(PlatformIcons.Property)
        }
        return when (type) {
          JsonSchemaType._object -> AllIcons.Json.Object
          JsonSchemaType._array -> AllIcons.Json.Array
          else -> getInstance().getPlatformIcon(PlatformIcons.Property)
        }
      }

      private fun hasSameType(variants: Collection<JsonSchemaObject>): Boolean {
        // enum is not a separate type, so we should treat whether it can be an enum distinctly from the types
        return variants.map {
          Pair(JsonSchemaObjectReadingUtils.guessType(it), isUntypedEnum(it))
        }.distinct().count() <= 1
      }

      private fun isUntypedEnum(it: JsonSchemaObject): Boolean {
        return JsonSchemaObjectReadingUtils.guessType(it) == null && !it.enum.isNullOrEmpty()
      }

      private fun createArrayOrObjectLiteralInsertHandler(newline: Boolean, insertedTextSize: Int): InsertHandler<LookupElement?> {
        return InsertHandler { context, _ ->
          val editor = context.editor
          if (!newline) {
            EditorModificationUtil.moveCaretRelatively(editor, -1)
          }
          else {
            EditorModificationUtil.moveCaretRelatively(editor, -insertedTextSize)
            PsiDocumentManager.getInstance(context.project).commitDocument(editor.document)
            invokeEnterHandler(editor)
            EditorActionUtil.moveCaretToLineEnd(editor, false, false)
          }
          AutoPopupController.getInstance(context.project).autoPopupMemberLookup(editor, null)
        }
      }

      private fun detectType(values: List<Any>): JsonSchemaType? {
        var type: JsonSchemaType? = null
        for (value in values) {
          var newType: JsonSchemaType? = null
          if (value is Int) newType = JsonSchemaType._integer
          if (type != null && type != newType) return null
          type = newType
        }
        return type
      }
    }
  }

  companion object {
    @JvmStatic
    fun doCompletion(parameters: CompletionParameters,
                     result: CompletionResultSet,
                     rootSchema: JsonSchemaObject,
                     stop: Boolean) {
      val completionPosition = if (parameters.originalPosition != null) parameters.originalPosition else parameters.position
      val worker = Worker(rootSchema, parameters.position, completionPosition!!, result)
      worker.work()
      // stop further completion only if the current contributor has at least one new completion variant
      if (stop && !worker.myVariants.isEmpty()) {
        result.stopHere()
      }
    }

    @JvmStatic
    @TestOnly
    fun getCompletionVariants(schema: JsonSchemaObject,
                              position: PsiElement, originalPosition: PsiElement): List<LookupElement> {
      val result: MutableList<LookupElement> = ArrayList()
      Worker(schema, position, originalPosition) { element: LookupElement -> result.add(element) }.work()
      return result
    }

    private fun updateStat(provider: JsonSchemaFileProvider?, schemaFile: VirtualFile?) {
      if (provider == null) {
        if (schemaFile is HttpVirtualFile) {
          // auto-detected and auto-downloaded JSON schemas
          JsonSchemaUsageTriggerCollector.trigger(REMOTE_USAGE_KEY)
        }
        return
      }
      val schemaType = provider.schemaType
      JsonSchemaUsageTriggerCollector.trigger(when (schemaType) {
                                                SchemaType.schema -> SCHEMA_USAGE_KEY
                                                SchemaType.userSchema -> USER_USAGE_KEY
                                                SchemaType.embeddedSchema -> BUILTIN_USAGE_KEY
                                                SchemaType.remoteSchema ->  // this works only for user-specified remote schemas in our settings, but not for auto-detected remote schemas
                                                  REMOTE_USAGE_KEY
                                              })
    }

    private fun invokeEnterHandler(editor: Editor) {
      val handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
      val caret = editor.caretModel.currentCaret
      handler.execute(editor, caret, CaretSpecificDataContext.create(
        DataManager.getInstance().getDataContext(editor.contentComponent), caret))
    }

    private fun handleInsideQuotesInsertion(context: InsertionContext, editor: Editor, hasValue: Boolean,
                                            insideStringLiteral: Boolean): Boolean {
      if (insideStringLiteral) {
        val offset = editor.caretModel.offset
        val element = context.file.findElementAt(offset)
        val tailOffset = context.tailOffset
        val guessEndOffset = tailOffset + 1
        if (element is LeafPsiElement) {
          if (handleIncompleteString(editor, element)) return false
          val endOffset = element.getTextRange().endOffset
          if (endOffset > tailOffset) {
            context.document.deleteString(tailOffset, endOffset - 1)
          }
        }
        if (hasValue) {
          return true
        }
        editor.caretModel.moveToOffset(guessEndOffset)
      }
      else {
        editor.caretModel.moveToOffset(context.tailOffset)
      }
      return false
    }

    private fun handleIncompleteString(editor: Editor, element: PsiElement): Boolean {
      if ((element as LeafPsiElement).elementType === TokenType.WHITE_SPACE) {
        val prevSibling = element.prevSibling
        if (prevSibling is JsonProperty) {
          val nameElement = prevSibling.nameElement
          if (!nameElement.text.endsWith("\"")) {
            editor.caretModel.moveToOffset(nameElement.textRange.endOffset)
            EditorModificationUtil.insertStringAtCaret(editor, "\"", false, true, 1)
            return true
          }
        }
      }
      return false
    }

    fun createPropertyInsertHandler(hasValue: Boolean,
                                    insertComma: Boolean,
                                    finalType: JsonSchemaType?,
                                    defaultValueAsString: String?,
                                    values: List<Any>?, walker: JsonLikePsiWalker,
                                    insideStringLiteral: Boolean): InsertHandler<LookupElement> {
      return InsertHandler { context, _ ->
        ApplicationManager.getApplication().assertWriteAccessAllowed()
        val editor = context.editor
        val project = context.project
        var stringToInsert: String? = null
        val comma = if (insertComma) "," else ""

        if (handleInsideQuotesInsertion(context, editor, hasValue, insideStringLiteral)) return@InsertHandler

        val propertyValueSeparator = walker.getPropertyValueSeparator(finalType)

        val element = context.file.findElementAt(editor.caretModel.offset)
        val insertColon = element == null || propertyValueSeparator != element.text
        if (!insertColon) {
          editor.caretModel.moveToOffset(editor.caretModel.offset + propertyValueSeparator.length)
        }
        if (finalType != null) {
          var hadEnter: Boolean
          when (finalType) {
            JsonSchemaType._object -> {
              EditorModificationUtil.insertStringAtCaret(editor, if (insertColon) ("$propertyValueSeparator ") else " ",
                                                         false, true,
                                                         if (insertColon) propertyValueSeparator.length + 1 else 1)
              hadEnter = false
              val invokeEnter = walker.hasWhitespaceDelimitedCodeBlocks()
              if (insertColon && invokeEnter) {
                invokeEnterHandler(editor)
                hadEnter = true
              }
              if (insertColon) {
                stringToInsert = walker.defaultObjectValue + comma
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                           false, true,
                                                           if (hadEnter) 0 else 1)
              }

              if (hadEnter || !insertColon) {
                EditorActionUtil.moveCaretToLineEnd(editor, false, false)
              }

              PsiDocumentManager.getInstance(project).commitDocument(editor.document)
              if (!hadEnter && stringToInsert != null) {
                formatInsertedString(context, stringToInsert.length)
              }
              if (stringToInsert != null && !invokeEnter) {
                invokeEnterHandler(editor)
              }
            }
            JsonSchemaType._boolean -> {
              val value = (true.toString() == defaultValueAsString).toString()
              stringToInsert = (if (insertColon) "$propertyValueSeparator " else " ") + value + comma
              val model = editor.selectionModel

              EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                         false, true,
                                                         stringToInsert.length - comma.length)
              formatInsertedString(context, stringToInsert.length)
              val start = editor.selectionModel.selectionStart
              model.setSelection(start - value.length, start)
              AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
            }
            JsonSchemaType._array -> {
              EditorModificationUtilEx.insertStringAtCaret(editor, if (insertColon) propertyValueSeparator else " ",
                                                           false, true,
                                                           propertyValueSeparator.length)
              hadEnter = false
              if (insertColon && walker.hasWhitespaceDelimitedCodeBlocks()) {
                invokeEnterHandler(editor)
                hadEnter = true
              }
              else {
                EditorModificationUtilEx.insertStringAtCaret(editor, " ", false, true, 1)
              }
              if (insertColon) {
                stringToInsert = walker.defaultArrayValue + comma
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                           false, true,
                                                           if (hadEnter) 0 else 1)
              }
              if (hadEnter) {
                EditorActionUtil.moveCaretToLineEnd(editor, false, false)
              }

              PsiDocumentManager.getInstance(project).commitDocument(editor.document)

              if (stringToInsert != null && walker.requiresReformatAfterArrayInsertion()) {
                formatInsertedString(context, stringToInsert.length)
              }
            }
            JsonSchemaType._string, JsonSchemaType._integer, JsonSchemaType._number -> insertPropertyWithEnum(context, editor,
                                                                                                              defaultValueAsString, values,
                                                                                                              finalType, comma, walker,
                                                                                                              insertColon)
            else -> {}
          }
        }
        else {
          insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma, walker, insertColon)
        }
      }
    }

    private fun insertPropertyWithEnum(context: InsertionContext,
                                       editor: Editor,
                                       defaultValue: String?,
                                       values: List<Any>?,
                                       type: JsonSchemaType?,
                                       comma: String,
                                       walker: JsonLikePsiWalker,
                                       insertColon: Boolean) {
      var value = defaultValue
      val propertyValueSeparator = walker.getPropertyValueSeparator(type)
      if (!walker.requiresValueQuotes() && value != null) {
        value = StringUtil.unquoteString(value)
      }
      val isNumber = type != null && (JsonSchemaType._integer == type || JsonSchemaType._number == type) ||
                     type == null && (value != null &&
                                      !StringUtil.isQuotedString(value) ||
                                      values != null && values.all { it !is String })
      val hasValues = !ContainerUtil.isEmpty(values)
      val hasDefaultValue = !StringUtil.isEmpty(value)
      val hasQuotes = isNumber || !walker.requiresValueQuotes()
      val offset = editor.caretModel.offset
      val charSequence = editor.document.charsSequence
      val ws = if (charSequence.length > offset && charSequence[offset] == ' ') "" else " "
      val colonWs = if (insertColon) propertyValueSeparator + ws else ws
      val stringToInsert = colonWs + (if (hasDefaultValue) value else (if (hasQuotes) "" else "\"\"")) + comma
      EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                                 if (insertColon) propertyValueSeparator.length + 1 else 1)
      if (!hasQuotes || hasDefaultValue) {
        val model = editor.selectionModel
        val caretStart = model.selectionStart
        var newOffset = caretStart + (if (hasDefaultValue) value!!.length else 1)
        if (hasDefaultValue && !hasQuotes) newOffset--
        model.setSelection(if (hasQuotes) caretStart else (caretStart + 1), newOffset)
        editor.caretModel.moveToOffset(newOffset)
      }

      if (!walker.hasWhitespaceDelimitedCodeBlocks() && stringToInsert != colonWs + comma) {
        formatInsertedString(context, stringToInsert.length)
      }

      if (hasValues) {
        AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
      }
    }

    fun formatInsertedString(context: InsertionContext,
                             offset: Int) {
      val project = context.project
      PsiDocumentManager.getInstance(project).commitDocument(context.document)
      val codeStyleManager = CodeStyleManager.getInstance(project)
      codeStyleManager.reformatText(context.file, context.startOffset, context.tailOffset + offset)
    }
  }
}