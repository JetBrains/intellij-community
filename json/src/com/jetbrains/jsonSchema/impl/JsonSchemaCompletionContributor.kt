// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.json.JsonBundle
import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.json.psi.*
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parents
import com.intellij.ui.IconManager.Companion.getInstance
import com.intellij.ui.PlatformIcons
import com.intellij.util.ObjectUtils
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonSchemaCompletionCustomizer
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaNestedCompletionsTreeProvider.Companion.getNestedCompletionsData
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_ENUM_ORDER_SENSITIVE
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_LANGUAGE_INJECTION
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils
import com.jetbrains.jsonSchema.impl.nestedCompletions.*
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest
import one.util.streamex.StreamEx
import javax.swing.Icon

private const val BUILTIN_USAGE_KEY = "builtin"
private const val SCHEMA_USAGE_KEY = "schema"
private const val USER_USAGE_KEY = "user"
private const val REMOTE_USAGE_KEY = "remote"

class JsonSchemaCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val file = PsiUtilCore.getVirtualFile(position) ?: return

    val service = JsonSchemaService.Impl.get(position.project)
    if (!service.isApplicableToFile(file)) return
    val rootSchema = service.getSchemaObject(position.containingFile) ?: return

    if (skipForSchemaAndRef(position, service, file)) return

    updateStat(service.getSchemaProvider(rootSchema), service.resolveSchemaFile(rootSchema))
    doCompletion(parameters, result, rootSchema, true)
  }

  private fun skipForSchemaAndRef(position: PsiElement,
                                  service: JsonSchemaService,
                                  file: VirtualFile): Boolean {
    val positionParent = position.parent ?: return false
    val parent = positionParent.parent as? JsonProperty ?: return false
    val propName = parent.name
    return ("\$schema" == propName && parent.parent is JsonObject && parent.parent.parent is JsonFile
            || "\$ref" == propName && service.isSchemaFile(file))
  }

  private class Worker(private val rootSchema: JsonSchemaObject,
                       private val completionPsiElement: PsiElement,
                       private val originalPosition: PsiElement,
                       private val completionType: CompletionType,
                       private val resultHandler: (Collection<LookupElement>) -> Unit) {
    private val wrapInQuotes: Boolean
    private val insideStringLiteral: Boolean

    // we need this set to filter same-named suggestions (they can be suggested by several matching schemes)
    val completionVariants = mutableSetOf<LookupElement>()
    private val psiWalker: JsonLikePsiWalker?
    private val myProject: Project = originalPosition.project

    init {
      val walker = JsonLikePsiWalker.getWalker(completionPsiElement, rootSchema)
      val positionParent = completionPsiElement.parent
      val isInsideQuotedString = (positionParent != null && walker != null && walker.isQuotedString(positionParent))
      wrapInQuotes = !isInsideQuotedString
      psiWalker = walker
      insideStringLiteral = isInsideQuotedString
    }

    private val customizers by lazy {
      JsonSchemaCompletionCustomizer.EXTENSION_POINT_NAME.extensionList
        .filter { it.isApplicable(originalPosition.containingFile) }
    }


    fun work() {
      if (psiWalker == null) return
      val checkable = psiWalker.findElementToCheck(completionPsiElement)
      if (checkable == null) return
      val isName = psiWalker.isName(checkable)
      val position = psiWalker.findPosition(checkable, isName == ThreeState.NO)
      if (position == null || position.isEmpty && isName == ThreeState.NO) return

      val knownNames = mutableSetOf<String>()

      val nestedCompletionsNode = getNestedCompletionsData(originalPosition.containingFile)
        .navigate(position)

      val schemaExpansionRequest = JsonSchemaNodeExpansionRequest(
        psiWalker.getParentPropertyAdapter(completionPsiElement)?.parentObject,
        completionType == CompletionType.SMART
      )
      val completionCustomizer = customizers.singleOrNull()
      JsonSchemaResolver(myProject, rootSchema, position, schemaExpansionRequest)
        .resolve()
        .forEach { schema ->
          schema.collectNestedCompletions(myProject, nestedCompletionsNode) { path, subSchema ->
            if (completionCustomizer?.acceptsPropertyCompletionItem(subSchema, completionPsiElement) == false)
              CompletionNextStep.Stop
            else {
              processSchema(subSchema, isName, knownNames, path)
              CompletionNextStep.Continue
            }
          }
        }

      resultHandler(completionVariants)
    }

    /**
     * @param completionPath Linked node representation of the names of all the parent
     * schema objects that we have navigated for nested completions
     */
    fun processSchema(schema: JsonSchemaObject,
                      isName: ThreeState,
                      knownNames: MutableSet<String>,
                      completionPath: SchemaPath?) {
      if (isName != ThreeState.NO) {
        val completionOriginalPosition = psiWalker!!.findChildBy(completionPath, originalPosition)
        val completionPosition = psiWalker.findChildBy(completionPath, completionPsiElement)

        val properties = psiWalker.getPropertyNamesOfParentObject(completionOriginalPosition, completionPosition)
        val adapter = psiWalker.getParentPropertyAdapter(completionOriginalPosition)

        val forbiddenNames = findPropertiesThatMustNotBePresent(schema, completionPsiElement, myProject, properties)
          .plus(properties)
        addAllPropertyVariants(schema, forbiddenNames, adapter, knownNames, completionPath)
        addPropertyNameSchemaVariants(schema)
      }

      if (isName != ThreeState.YES) {
        suggestValues(schema, isName == ThreeState.NO, completionPath)
      }
    }

    fun addPropertyNameSchemaVariants(schema: JsonSchemaObject) {
      val anEnum = schema.propertyNamesSchema?.enum ?: return
      for (o in anEnum) {
        if (o !is String) continue
        completionVariants.add(LookupElementBuilder.create(StringUtil.unquoteString(
          if (!shouldWrapInQuotes(o, false)) o else StringUtil.wrapWithDoubleQuote(o)
        )))
      }
    }

    fun addAllPropertyVariants(schema: JsonSchemaObject,
                               forbiddenNames: Set<String>,
                               adapter: JsonPropertyAdapter?,
                               knownNames: MutableSet<String>,
                               completionPath: SchemaPath?) {
      StreamEx.of(schema.propertyNames)
        .filter { name -> !forbiddenNames.contains(name) && !knownNames.contains(name) || adapter != null && name == adapter.name }
        .forEach { name ->
          knownNames.add(name)
          val propertySchema = checkNotNull(schema.getPropertyByName(name))
          if (customizers.singleOrNull()?.acceptsPropertyCompletionItem(propertySchema, completionPsiElement) != false) {
            addPropertyVariant(name, propertySchema, completionPath, adapter?.nameValueAdapter)
          }
        }
    }

    fun suggestValues(schema: JsonSchemaObject, isSurelyValue: Boolean, completionPath: SchemaPath?) {
      suggestValuesForSchemaVariants(schema.anyOf, isSurelyValue, completionPath)
      suggestValuesForSchemaVariants(schema.oneOf, isSurelyValue, completionPath)
      suggestValuesForSchemaVariants(schema.allOf, isSurelyValue, completionPath)

      if (schema.enum != null && completionPath == null) {
        val metadata = schema.enumMetadata
        val isEnumOrderSensitive = schema.readChildNodeValue(X_INTELLIJ_ENUM_ORDER_SENSITIVE).toBoolean()
        val anEnum = schema.enum
        val filtered = filteredByDefault + getEnumItemsToSkip()
        for (i in anEnum!!.indices) {
          val o = anEnum[i]
          if (insideStringLiteral && o !is String) continue
          val variant = o.toString()
          if (!filtered.contains(variant) && !filtered.contains(StringUtil.unquoteString(variant))) {
            val valueMetadata = metadata?.get(StringUtil.unquoteString(variant))
            val description = valueMetadata?.get("description")
            val deprecated = valueMetadata?.get("deprecationMessage")
            val order = if (isEnumOrderSensitive) i else null
            val handlers = customizers.mapNotNull { p -> p.createHandlerForEnumValue(schema, variant) }.toList()
            addValueVariant(
              key = variant,
              description = description,
              deprecated = deprecated != null,
              handler = handlers.singleOrNull(),
              order = order
            )
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

    private fun getEnumItemsToSkip(): Set<String> {
      // if the parent is an array, and it assumes unique items, we don't suggest the same enum items again
      val position = psiWalker?.findPosition(psiWalker.findElementToCheck(completionPsiElement), false)
      val containerSchema = position?.trimTail(1)?.let { JsonSchemaResolver(myProject, rootSchema, it, null) }?.resolve()?.singleOrNull()
      return if (psiWalker != null && containerSchema?.isUniqueItems == true) {
        val parentArray = completionPsiElement.parents(false).firstNotNullOfOrNull {
          psiWalker.createValueAdapter(it)?.asArray
        }
        parentArray?.elements.orEmpty().map { StringUtil.unquoteString(it.delegate.text) }.toSet()
      }
      else emptySet()
    }

    fun suggestSpecialValues(type: JsonSchemaType?) {
      if (!JsonSchemaVersion.isSchemaSchemaId(rootSchema.id) || type != JsonSchemaType._string) return
      val propertyAdapter = psiWalker!!.getParentPropertyAdapter(originalPosition) ?: return
      when (propertyAdapter.name) {
        "required" -> addRequiredPropVariants()
        X_INTELLIJ_LANGUAGE_INJECTION -> addInjectedLanguageVariants()
        "language" -> {
          val parent = propertyAdapter.parentObject
          if (parent != null) {
            val adapter = psiWalker.getParentPropertyAdapter(parent.delegate)
            if (adapter != null && X_INTELLIJ_LANGUAGE_INJECTION == adapter.name) {
              addInjectedLanguageVariants()
            }
          }
        }
      }
    }

    fun addInjectedLanguageVariants() {
      val checkable = psiWalker!!.findElementToCheck(completionPsiElement)
      if (checkable !is JsonStringLiteral && checkable !is JsonReferenceExpression) return
      Language.getRegisteredLanguages()
        .filter { LanguageUtil.isInjectableLanguage(it) }
        .map { Injectable.fromLanguage(it) }
        .forEach {
          completionVariants.add(
            LookupElementBuilder
              .create(it.id)
              .withIcon(it.icon)
              .withTailText("(" + it.displayName + ")", true)
          )
        }
    }

    fun addRequiredPropVariants() {
      val checkable = psiWalker!!.findElementToCheck(completionPsiElement)
      if (checkable !is JsonStringLiteral && checkable !is JsonReferenceExpression) return
      val propertiesObject = JsonRequiredPropsReferenceProvider.findPropertiesObject(checkable) ?: return
      val parent = checkable.parent
      val items = if (parent is JsonArray) {
        parent.valueList.filterIsInstance<JsonStringLiteral>().map { it.value }.toSet()
      }
      else emptySet()
      propertiesObject.propertyList.map { it.name }.filter { !items.contains(it) }
        .forEach { addStringVariant(it) }
    }

    fun suggestByType(schema: JsonSchemaObject, type: JsonSchemaType) {
      if (JsonSchemaType._string == type) {
        addPossibleStringValue(schema)
      }
      if (insideStringLiteral) {
        return
      }
      when (type) {
        JsonSchemaType._boolean -> {
          addValueVariant("true")
          addValueVariant("false")
        }
        JsonSchemaType._null -> {
          addValueVariant("null")
        }
        JsonSchemaType._array -> {
          val value = psiWalker!!.defaultArrayValue
          addValueVariant(
            key = value,
            altText = "[...]",
            handler = createArrayOrObjectLiteralInsertHandler(
              psiWalker.hasWhitespaceDelimitedCodeBlocks(), value.length
            )
          )
        }
        JsonSchemaType._object -> {
          val value = psiWalker!!.defaultObjectValue
          addValueVariant(
            key = value,
            altText = "{...}",
            handler = createArrayOrObjectLiteralInsertHandler(
              psiWalker.hasWhitespaceDelimitedCodeBlocks(), value.length
            )
          )
        }
        else -> { /* no suggestions */
        }
      }
    }

    fun addPossibleStringValue(schema: JsonSchemaObject) {
      val defaultValue = schema.default
      val defaultValueString = defaultValue?.toString()
      addStringVariant(defaultValueString)
    }

    fun addStringVariant(defaultValueString: String?) {
      if (defaultValueString == null) return
      var normalizedValue: String = defaultValueString
      val shouldQuote = psiWalker!!.requiresValueQuotes()
      val isQuoted = StringUtil.isQuotedString(normalizedValue)
      if (shouldQuote && !isQuoted) {
        normalizedValue = StringUtil.wrapWithDoubleQuote(normalizedValue)
      }
      else if (!shouldQuote && isQuoted) {
        normalizedValue = StringUtil.unquoteString(normalizedValue)
      }
      addValueVariant(normalizedValue)
    }

    fun suggestValuesForSchemaVariants(list: List<JsonSchemaObject>?, isSurelyValue: Boolean, completionPath: SchemaPath?) {
      if (list.isNullOrEmpty()) return
      for (schemaObject in list) {
        suggestValues(schemaObject, isSurelyValue, completionPath)
      }
    }

    fun addValueVariant(key: String,
                        description: String? = null,
                        altText: String? = null,
                        handler: InsertHandler<LookupElement?>? = null,
                        order: Int? = null,
                        deprecated: Boolean = false) {
      val unquoted = StringUtil.unquoteString(key)
      val lookupString = if (!shouldWrapInQuotes(unquoted, true)) unquoted else key
      val builder = LookupElementBuilder.create(lookupString)
        .withPresentableText(altText ?: lookupString)
        .withTypeText(description)
        .withInsertHandler(handler)
        .withDeprecation(deprecated)
      if (order != null) {
        completionVariants.add(PrioritizedLookupElement.withPriority(builder, -order.toDouble()))
      }
      else {
        completionVariants.add(builder)
      }
    }

    fun shouldWrapInQuotes(key: String?, isValue: Boolean): Boolean {
      return wrapInQuotes && psiWalker != null &&
             (isValue && psiWalker.requiresValueQuotes() || !isValue && psiWalker.requiresNameQuotes() || !psiWalker.isValidIdentifier(key,
                                                                                                                                       myProject))
    }

    fun addPropertyVariant(key: String,
                           jsonSchemaObject: JsonSchemaObject,
                           completionPath: SchemaPath?,
                           sourcePsiAdapter: JsonValueAdapter?) {
      var propertyKey = key
      var schemaObject = jsonSchemaObject
      val variants = JsonSchemaResolver(myProject, schemaObject, JsonPointerPosition(), sourcePsiAdapter).resolve()
      schemaObject = ObjectUtils.coalesce(variants.firstOrNull(), schemaObject)
      propertyKey = if (!shouldWrapInQuotes(propertyKey, false)) propertyKey else StringUtil.wrapWithDoubleQuote(propertyKey)

      val builder = LookupElementBuilder.create(propertyKey)
        .withPresentableText(completionPath?.let { it.prefix() + "." + key }
                             ?: key.takeIf { psiWalker?.requiresNameQuotes() == false }
                             ?: propertyKey)
        .withLookupStrings(listOfNotNull(completionPath?.let { it.prefix() + "." + key }, propertyKey) + completionPath?.accessor().orEmpty())
        .withTypeText(getDocumentationOrTypeName(schemaObject), true)
        .withIcon(getIcon(JsonSchemaObjectReadingUtils.guessType(schemaObject)))
        .withInsertHandler(choosePropertyInsertHandler(completionPath, variants, schemaObject))
        .withDeprecation(schemaObject.deprecationMessage != null)

      if (completionPath != null) {
        completionVariants.add(PrioritizedLookupElement.withPriority(builder, -completionPath.accessor().size.toDouble()))
      }
      else {
        completionVariants.add(builder)
      }
    }

    private fun LookupElementBuilder.withDeprecation(deprecated: Boolean): LookupElementBuilder {
      if (!deprecated) return this
      return withTailText(JsonBundle.message("schema.documentation.deprecated.postfix"), true).withStrikeoutness(true)
    }

    private fun choosePropertyInsertHandler(completionPath: SchemaPath?,
                                            variants: Collection<JsonSchemaObject>,
                                            schemaObject: JsonSchemaObject): InsertHandler<LookupElement> {
      if (hasSameType(variants)) {
        val type = JsonSchemaObjectReadingUtils.guessType(schemaObject)
        val values = schemaObject.enum
        if (!values.isNullOrEmpty()) {
          // if we have an enum with a single kind of values - trigger the handler with value
          if (values.map { v -> v.javaClass }.distinct().count() == 1) {
            return createPropertyInsertHandler(schemaObject, completionPath)
          }
        }
        else {
          // insert a default value if no enum
          if (type != null || schemaObject.default != null) {
            return createPropertyInsertHandler(schemaObject, completionPath)
          }
        }
      }

      return createDefaultPropertyInsertHandler(completionPath, !schemaObject.enum.isNullOrEmpty(),
                                                schemaObject.type)
    }

    private fun getDocumentationOrTypeName(schemaObject: JsonSchemaObject): String? {
      val docText = JsonSchemaDocumentationProvider.getBestDocumentation(true, schemaObject)
      return if (!docText.isNullOrBlank()) {
        findFirstSentence(StringUtil.removeHtmlTags(docText))
      }
      else {
        JsonSchemaObjectReadingUtils.getTypeDescription(schemaObject, true)
      }
    }

    private fun createDefaultPropertyInsertHandler(completionPath: SchemaPath? = null,
                                                   hasEnumValues: Boolean = false,
                                                   valueType: JsonSchemaType? = null): InsertHandler<LookupElement> {
      return object : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
          ApplicationManager.getApplication().assertWriteAccessAllowed()
          val editor = context.editor
          val project = context.project

          expandMissingPropertiesAndMoveCaret(context, completionPath)

          if (handleInsideQuotesInsertion(context, editor, insideStringLiteral)) return
          val insertComma = psiWalker?.hasMissingCommaAfter(completionPsiElement) == true
          val comma = if (insertComma) "," else ""
          val hasValue = hasEnumValues || psiWalker?.let {
            it.isPropertyWithValue(it.findElementToCheck(completionPsiElement))
          } == true
          var offset = editor.caretModel.offset
          val initialOffset = offset
          val docChars = context.document.charsSequence
          while (offset < docChars.length && Character.isWhitespace(docChars[offset])) {
            offset++
          }
          val propertyValueSeparator = psiWalker!!.getPropertyValueSeparator(valueType)
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
            val stringToInsert = "$propertyValueSeparator 1$comma"
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
                                    completionPath: SchemaPath?): InsertHandler<LookupElement> {
      val defaultValueAsString = when (val defaultValue = jsonSchemaObject.default) {
        null, is JsonSchemaObject -> null
        is String -> "\"" + defaultValue + "\""
        else -> defaultValue.toString()
      }
      val finalType = JsonSchemaObjectReadingUtils.guessType(jsonSchemaObject) ?: detectTypeByEnumValues(jsonSchemaObject.enum.orEmpty())
      return createPropertyInsertHandler(finalType, defaultValueAsString, jsonSchemaObject.enum, psiWalker!!, insideStringLiteral, completionPath)
    }

    companion object {
      // some schemas provide an empty array or an empty object in enum values...
      private val filteredByDefault = setOf("[]", "{}", "[ ]", "{ }")
      private val commonAbbreviations = listOf("e.g.", "i.e.")

      private fun findFirstSentence(sentence: String): String {
        var i = sentence.indexOf(". ")
        while (i >= 0) {
          if (commonAbbreviations.none { abbr ->
              sentence.regionMatches(i - abbr.length + 1, abbr, 0, abbr.length)
            }) {
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

      private fun detectTypeByEnumValues(values: List<Any>): JsonSchemaType? {
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

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmStatic
    fun doCompletion(parameters: CompletionParameters,
                     result: CompletionResultSet,
                     rootSchema: JsonSchemaObject,
                     stop: Boolean) {
      val worker = Worker(rootSchema,
                          parameters.position,
                          parameters.originalPosition ?: parameters.position,
                          parameters.completionType) {
        result.addAllElements(it)
      }
      worker.work()
      // stop further completion only if the current contributor has at least one new completion variant
      if (stop && !worker.completionVariants.isEmpty()) {
        result.stopHere()
      }
    }

    @JvmStatic
    fun getCompletionVariants(schema: JsonSchemaObject,
                              position: PsiElement,
                              originalPosition: PsiElement,
                              completionType: CompletionType): List<LookupElement> {
      val result: MutableList<LookupElement> = ArrayList()
      Worker(schema, position, originalPosition, completionType) { elements -> result.addAll(elements) }.work()
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
      handler.execute(editor, caret, EditorActionHandler.caretDataContext(
        DataManager.getInstance().getDataContext(editor.contentComponent), caret))
    }

    private fun handleInsideQuotesInsertion(context: InsertionContext, editor: Editor, insideStringLiteral: Boolean): Boolean {
      if (!insideStringLiteral) return false
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
      if (element != null) {
        val walker = JsonLikePsiWalker.getWalker(element)
        if (walker != null && walker.isPropertyWithValue(walker.findElementToCheck(element))) return true
      }
      editor.caretModel.moveToOffset(guessEndOffset)
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

    fun createPropertyInsertHandler(finalType: JsonSchemaType?,
                                    defaultValueAsString: String?,
                                    values: List<Any>?,
                                    walker: JsonLikePsiWalker, insideStringLiteral: Boolean,
                                    completionPath: SchemaPath? = null): InsertHandler<LookupElement> {
      return InsertHandler { context, _ ->
        ThreadingAssertions.assertWriteAccess()
        val editor = context.editor
        val project = context.project

        expandMissingPropertiesAndMoveCaret(context, completionPath)

        var stringToInsert: String? = null

        if (handleInsideQuotesInsertion(context, editor, insideStringLiteral)) return@InsertHandler

        val propertyValueSeparator = walker.getPropertyValueSeparator(finalType)

        val leafAtCaret = findLeafAtCaret(context, editor, walker)
        val insertComma = leafAtCaret?.let { walker.hasMissingCommaAfter(it) } == true
        val comma = if (insertComma) "," else ""
        val insertColon = propertyValueSeparator != leafAtCaret?.text
        if (leafAtCaret != null && !insertColon) {
          editor.caretModel.moveToOffset(leafAtCaret.endOffset)
        }
        if (finalType != null) {
          var hadEnter: Boolean
          when (finalType) {
            JsonSchemaType._object -> {
              if (insertColon) {
                EditorModificationUtil.insertStringAtCaret(editor, "$propertyValueSeparator ",
                                                           false, true,
                                                           propertyValueSeparator.length + 1)
              }
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
              if (insertColon) {
                EditorModificationUtilEx.insertStringAtCaret(editor, propertyValueSeparator,
                                                             false, true,
                                                             propertyValueSeparator.length)
              }
              hadEnter = false
              val nextSibling = findLeafAtCaret(context, editor, walker)?.nextSibling
              if (insertColon && walker.hasWhitespaceDelimitedCodeBlocks()) {
                invokeEnterHandler(editor)
                hadEnter = true
              }
              else {
                if (nextSibling !is PsiWhiteSpace) {
                  EditorModificationUtilEx.insertStringAtCaret(editor, " ", false, true, 1)
                }
                else {
                  editor.caretModel.moveToOffset(nextSibling.endOffset)
                }
              }
              if (insertColon || findLeafAtCaret(context, editor, walker)?.text == walker.getPropertyValueSeparator(null)) {
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

    private fun findLeafAtCaret(context: InsertionContext,
                               editor: Editor,
                               walker: JsonLikePsiWalker) =
      context.file.findElementAt(editor.caretModel.offset)?.let {
        rewindToMeaningfulLeaf(it)
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
      val requiresQuotes = !isNumber && walker.requiresValueQuotes()
      val offset = editor.caretModel.offset
      val charSequence = editor.document.charsSequence
      val ws = if (charSequence.length > offset && charSequence[offset] == ' ') "" else " "
      val colonWs = if (insertColon) propertyValueSeparator + ws else ws
      val stringToInsert = colonWs + when {
        hasDefaultValue -> value
        requiresQuotes -> "\"\""
        else -> ""
      } + comma
      EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                                 if (insertColon) propertyValueSeparator.length + 1 else 1)
      if (requiresQuotes || hasDefaultValue) {
        val model = editor.selectionModel
        val caretStart = model.selectionStart
        // if we are already within the value quotes, then the shift is zero, if not yet - move inside
        val quoteOffset = if (
          caretStart - 1 >= 0
          && editor.document.charsSequence[caretStart - 1].let {
            it == '"' || it == '\''
          }) 0 else 1
        var newOffset = caretStart + (if (hasDefaultValue) value!!.length else quoteOffset)
        if (hasDefaultValue && requiresQuotes) newOffset--
        model.setSelection(if (requiresQuotes) (caretStart + quoteOffset) else caretStart, newOffset)
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

class JsonSchemaMetadataEntry(
  val key: String,
  val values: List<String>
)