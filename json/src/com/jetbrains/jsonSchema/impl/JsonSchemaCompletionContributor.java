// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.CaretSpecificDataContext;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.injection.Injectable;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsKt;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNodeKt;
import com.jetbrains.jsonSchema.impl.nestedCompletions.SchemaPath;
import kotlin.Unit;
import kotlin.collections.SetsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.NotRequiredPropertiesKt.effectiveBranchOrNull;
import static com.jetbrains.jsonSchema.impl.NotRequiredPropertiesKt.findPropertiesThatMustNotBePresent;

public final class JsonSchemaCompletionContributor extends CompletionContributor {
  private static final String BUILTIN_USAGE_KEY = "builtin";
  private static final String SCHEMA_USAGE_KEY = "schema";
  private static final String USER_USAGE_KEY = "user";
  private static final String REMOTE_USAGE_KEY = "remote";

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final VirtualFile file = PsiUtilCore.getVirtualFile(position);
    if (file == null) return;

    final JsonSchemaService service = JsonSchemaService.Impl.get(position.getProject());
    if (!service.isApplicableToFile(file)) return;
    final JsonSchemaObject rootSchema = service.getSchemaObject(position.getContainingFile());
    if (rootSchema == null) return;
    PsiElement positionParent = position.getParent();
    if (positionParent != null) {
      PsiElement parent = positionParent.getParent();
      if (parent instanceof JsonProperty) {
        final String propName = ((JsonProperty)parent).getName();
        if ("$schema".equals(propName) && parent.getParent() instanceof JsonObject && parent.getParent().getParent() instanceof JsonFile
            || "$ref".equals(propName) && service.isSchemaFile(file)) {
          return;
        }
      }
    }

    updateStat(service.getSchemaProvider(rootSchema), service.resolveSchemaFile(rootSchema));
    doCompletion(parameters, result, rootSchema, true);
  }

  public static void doCompletion(final @NotNull CompletionParameters parameters,
                                  final @NotNull CompletionResultSet result,
                                  final @NotNull JsonSchemaObject rootSchema,
                                  boolean stop) {
    final PsiElement completionPosition = parameters.getOriginalPosition() != null ? parameters.getOriginalPosition() :
                                          parameters.getPosition();
    Worker worker = new Worker(rootSchema, parameters.getPosition(), completionPosition, result);
    worker.work();
    // stop further completion only if current contributor has at least one new completion variant
    if (stop && !worker.myVariants.isEmpty()) {
      result.stopHere();
    }
  }

  @TestOnly
  public static @NotNull List<LookupElement> getCompletionVariants(final @NotNull JsonSchemaObject schema,
                                                                   final @NotNull PsiElement position, final @NotNull PsiElement originalPosition) {
    final List<LookupElement> result = new ArrayList<>();
    new Worker(schema, position, originalPosition, element -> result.add(element)).work();
    return result;
  }

  private static void updateStat(@Nullable JsonSchemaFileProvider provider, VirtualFile schemaFile) {
    if (provider == null) {
      if (schemaFile instanceof HttpVirtualFile) {
        // auto-detected and auto-downloaded JSON schemas
        JsonSchemaUsageTriggerCollector.trigger(REMOTE_USAGE_KEY);
      }
      return;
    }
    final SchemaType schemaType = provider.getSchemaType();
    JsonSchemaUsageTriggerCollector.trigger(switch (schemaType) {
      case schema -> SCHEMA_USAGE_KEY;
      case userSchema -> USER_USAGE_KEY;
      case embeddedSchema -> BUILTIN_USAGE_KEY;
      case remoteSchema ->
        // this works only for user-specified remote schemas in our settings, but not for auto-detected remote schemas
        REMOTE_USAGE_KEY;
    });
  }

  private static final class Worker {
    private final @NotNull JsonSchemaObject myRootSchema;
    private final @NotNull PsiElement myPosition;
    private final @NotNull PsiElement myOriginalPosition;
    private final @NotNull Consumer<LookupElement> myResultConsumer;
    private final boolean myWrapInQuotes;
    private final boolean myInsideStringLiteral;
    // we need this set to filter same-named suggestions (they can be suggested by several matching schemes)
    private final Set<LookupElement> myVariants;
    private final JsonLikePsiWalker myWalker;
    private final Project myProject;

    Worker(@NotNull JsonSchemaObject rootSchema, @NotNull PsiElement position,
           @NotNull PsiElement originalPosition, final @NotNull Consumer<LookupElement> resultConsumer) {
      myRootSchema = rootSchema;
      myPosition = position;
      myOriginalPosition = originalPosition;
      myProject = originalPosition.getProject();
      myResultConsumer = resultConsumer;
      myVariants = new HashSet<>();
      myWalker = JsonLikePsiWalker.getWalker(myPosition, myRootSchema);
      myWrapInQuotes = !(position.getParent() instanceof JsonStringLiteral);
      myInsideStringLiteral = position.getParent() instanceof JsonStringLiteral;
    }

    public void work() {
      if (myWalker == null) return;
      final PsiElement checkable = myWalker.findElementToCheck(myPosition);
      if (checkable == null) return;
      final ThreeState isName = myWalker.isName(checkable);
      final JsonPointerPosition position = myWalker.findPosition(checkable, isName == ThreeState.NO);
      if (position == null || position.isEmpty() && isName == ThreeState.NO) return;

      final Set<String> knownNames = new HashSet<>();

      final var nestedCompletionsNode = NestedCompletionsNodeKt.navigate(
        JsonSchemaNestedCompletionsTreeProvider.getNestedCompletionsData(myOriginalPosition.getContainingFile()),
        position
      );

      new JsonSchemaResolver(myProject, myRootSchema, position)
        .resolve()
        .forEach(schema -> {
          NestedCompletionsKt.collectNestedCompletions(schema, myProject, nestedCompletionsNode, null, (path, subSchema) -> {
            processSchema(subSchema, isName, checkable, knownNames, path);
            return Unit.INSTANCE;
          });
        });

      for (LookupElement variant : myVariants) {
        myResultConsumer.consume(variant);
      }
    }

    /**
     * @param completionPath Linked node representation of the names of all the parent
     *                       schema objects that we have navigated for nested completions
     */
    private void processSchema(JsonSchemaObject schema,
                               ThreeState isName,
                               PsiElement checkable,
                               Set<String> knownNames,
                               @Nullable SchemaPath completionPath) {
      if (isName != ThreeState.NO) {
        final var completionOriginalPosition = NestedCompletionsKt.findChildBy(myWalker, completionPath, myOriginalPosition);
        final var completionPosition = NestedCompletionsKt.findChildBy(myWalker, completionPath, myPosition);
        final boolean insertComma = myWalker.hasMissingCommaAfter(myPosition);
        final boolean hasValue = myWalker.isPropertyWithValue(checkable);

        final Set<String> properties = myWalker.getPropertyNamesOfParentObject(completionOriginalPosition, completionPosition);
        final JsonPropertyAdapter adapter = myWalker.getParentPropertyAdapter(completionOriginalPosition);

        final Map<String, JsonSchemaObject> schemaProperties = schema.getProperties();
        final Set<String> forbiddenNames = SetsKt.plus(
          findPropertiesThatMustNotBePresent(schema, myPosition, myProject, properties),
          properties
        );
        addAllPropertyVariants(insertComma, hasValue, forbiddenNames, adapter, schemaProperties, knownNames, completionPath);
        addIfThenElsePropertyNameVariants(schema, insertComma, hasValue, forbiddenNames, adapter, knownNames, completionPath);
        addPropertyNameSchemaVariants(schema);
      }

      if (isName != ThreeState.YES) {
        suggestValues(schema, isName == ThreeState.NO);
      }
    }

    private void addPropertyNameSchemaVariants(@NotNull JsonSchemaObject schema) {
      JsonSchemaObject propertyNamesSchema = schema.getPropertyNamesSchema();
      if (propertyNamesSchema == null) return;
      List<Object> anEnum = propertyNamesSchema.getEnum();
      if (anEnum == null) return;
      for (Object o : anEnum) {
        if (!(o instanceof String key)) continue;
        key = !shouldWrapInQuotes(key, false) ? key : StringUtil.wrapWithDoubleQuote(key);
        myVariants.add(LookupElementBuilder.create(StringUtil.unquoteString(key)));
      }
    }

    private void addIfThenElsePropertyNameVariants(@NotNull JsonSchemaObject schema,
                                                   boolean insertComma,
                                                   boolean hasValue,
                                                   @NotNull Set<String> forbiddenNames,
                                                   @Nullable JsonPropertyAdapter adapter,
                                                   Set<String> knownNames,
                                                   @Nullable SchemaPath completionPath) {
      List<IfThenElse> ifThenElseList = schema.getIfThenElse();
      if (ifThenElseList == null) return;

      JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(myPosition, schema);
      JsonPropertyAdapter propertyAdapter = walker == null ? null : walker.getParentPropertyAdapter(myPosition);
      if (propertyAdapter == null) return;

      JsonObjectValueAdapter object = propertyAdapter.getParentObject();
      if (object == null) return;

      for (IfThenElse ifThenElse : ifThenElseList) {
        JsonSchemaObject effectiveBranch = effectiveBranchOrNull(ifThenElse, myProject, object);
        if (effectiveBranch == null) continue;

        addAllPropertyVariants(insertComma, hasValue, forbiddenNames, adapter, effectiveBranch.getProperties(), knownNames, completionPath);
      }
    }

    private void addAllPropertyVariants(boolean insertComma,
                                        boolean hasValue,
                                        Set<String> forbiddenNames,
                                        JsonPropertyAdapter adapter,
                                        Map<String, JsonSchemaObject> schemaProperties,
                                        Set<String> knownNames,
                                        @Nullable SchemaPath completionPath) {
      schemaProperties.keySet().stream()
        .filter(name -> !forbiddenNames.contains(name) && !knownNames.contains(name) || adapter != null && name.equals(adapter.getName()))
        .forEach(name -> {
          knownNames.add(name);
          addPropertyVariant(name, schemaProperties.get(name), hasValue, insertComma, completionPath);
        });
    }

    // some schemas provide empty array / empty object in enum values...
    private static final Set<String> filtered = Set.of("[]", "{}", "[ ]", "{ }");

    private void suggestValues(JsonSchemaObject schema, boolean isSurelyValue) {
      suggestValuesForSchemaVariants(schema.getAnyOf(), isSurelyValue);
      suggestValuesForSchemaVariants(schema.getOneOf(), isSurelyValue);
      suggestValuesForSchemaVariants(schema.getAllOf(), isSurelyValue);

      if (schema.getEnum() != null) {
        Map<String, Map<String, String>> metadata = schema.getEnumMetadata();
        for (Object o : schema.getEnum()) {
          if (myInsideStringLiteral && !(o instanceof String)) continue;
          String variant = o.toString();
          if (!filtered.contains(variant)) {
            Map<String, String> valueMetadata = metadata == null ? null : metadata.get(StringUtil.unquoteString(variant));
            String description = valueMetadata == null ? null : valueMetadata.get("description");
            String deprecated = valueMetadata == null ? null : valueMetadata.get("deprecationMessage");
            addValueVariant(variant, description, deprecated != null ? (variant + " (" + deprecated + ")") : null, null);
          }
        }
      }
      else if (isSurelyValue) {
        final JsonSchemaType type = schema.guessType();
        suggestSpecialValues(type);
        if (type != null) {
          suggestByType(schema, type);
        }
        else if (schema.getTypeVariants() != null) {
          for (JsonSchemaType schemaType : schema.getTypeVariants()) {
            suggestByType(schema, schemaType);
          }
        }
      }
    }

    private void suggestSpecialValues(@Nullable JsonSchemaType type) {
      if (JsonSchemaVersion.isSchemaSchemaId(myRootSchema.getId()) && type == JsonSchemaType._string) {
        JsonPropertyAdapter propertyAdapter = myWalker.getParentPropertyAdapter(myOriginalPosition);
        if (propertyAdapter == null) {
          return;
        }
        String name = propertyAdapter.getName();
        if (name == null) {
          return;
        }
        switch (name) {
          case "required" -> addRequiredPropVariants();
          case JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION -> addInjectedLanguageVariants();
          case "language" -> {
            JsonObjectValueAdapter parent = propertyAdapter.getParentObject();
            if (parent != null) {
              JsonPropertyAdapter adapter = myWalker.getParentPropertyAdapter(parent.getDelegate());
              if (adapter != null && JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION.equals(adapter.getName())) {
                addInjectedLanguageVariants();
              }
            }
          }
        }
      }
    }

    private void addInjectedLanguageVariants() {
      PsiElement checkable = myWalker.findElementToCheck(myPosition);
      if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) return;
      JBIterable.from(Language.getRegisteredLanguages())
        .filter(LanguageUtil::isInjectableLanguage)
        .map(Injectable::fromLanguage)
        .forEach(it -> myVariants.add(LookupElementBuilder
                                        .create(it.getId())
                                        .withIcon(it.getIcon())
                                        .withTailText("(" + it.getDisplayName() + ")", true)));
    }

    private void addRequiredPropVariants() {
      PsiElement checkable = myWalker.findElementToCheck(myPosition);
      if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) return;
      JsonObject propertiesObject = JsonRequiredPropsReferenceProvider.findPropertiesObject(checkable);
      if (propertiesObject == null) return;
      PsiElement parent = checkable.getParent();
      Set<String> items = parent instanceof JsonArray
                          ? ((JsonArray)parent).getValueList().stream()
                            .filter(v -> v instanceof JsonStringLiteral).map(v -> ((JsonStringLiteral)v).getValue())
                            .collect(Collectors.toSet())
                          : new HashSet<>();
      propertiesObject.getPropertyList().stream().map(p -> p.getName()).filter(n -> !items.contains(n))
        .forEach(n -> addStringVariant(n));
    }

    private void suggestByType(JsonSchemaObject schema, JsonSchemaType type) {
      if (JsonSchemaType._string.equals(type)) {
        addPossibleStringValue(schema);
      }
      if (myInsideStringLiteral) {
        return;
      }
      if (JsonSchemaType._boolean.equals(type)) {
        addPossibleBooleanValue(type);
      }
      else if (JsonSchemaType._null.equals(type)) {
        addValueVariant("null", null);
      }
      else if (JsonSchemaType._array.equals(type)) {
        String value = myWalker.getDefaultArrayValue();
        addValueVariant(value, null,
                        "[...]", createArrayOrObjectLiteralInsertHandler(myWalker.hasWhitespaceDelimitedCodeBlocks(), value.length())
        );
      }
      else if (JsonSchemaType._object.equals(type)) {
        String value = myWalker.getDefaultObjectValue();
        addValueVariant(value, null,
                        "{...}", createArrayOrObjectLiteralInsertHandler(myWalker.hasWhitespaceDelimitedCodeBlocks(), value.length())
        );
      }
    }

    private void addPossibleStringValue(JsonSchemaObject schema) {
      Object defaultValue = schema.getDefault();
      String defaultValueString = defaultValue == null ? null : defaultValue.toString();
      addStringVariant(defaultValueString);
    }

    private void addStringVariant(String defaultValueString) {
      if (defaultValueString != null) {
        String normalizedValue = defaultValueString;
        boolean shouldQuote = myWalker.requiresValueQuotes();
        boolean isQuoted = StringUtil.isQuotedString(normalizedValue);
        if (shouldQuote && !isQuoted) {
          normalizedValue = StringUtil.wrapWithDoubleQuote(normalizedValue);
        }
        else if (!shouldQuote && isQuoted) {
          normalizedValue = StringUtil.unquoteString(normalizedValue);
        }
        addValueVariant(normalizedValue, null);
      }
    }

    private void suggestValuesForSchemaVariants(List<JsonSchemaObject> list, boolean isSurelyValue) {
      if (list != null && list.size() > 0) {
        for (JsonSchemaObject schemaObject : list) {
          suggestValues(schemaObject, isSurelyValue);
        }
      }
    }

    private void addPossibleBooleanValue(JsonSchemaType type) {
      if (JsonSchemaType._boolean.equals(type)) {
        addValueVariant("true", null);
        addValueVariant("false", null);
      }
    }


    private void addValueVariant(@NotNull String key, @SuppressWarnings("SameParameterValue") final @Nullable String description) {
      addValueVariant(key, description, null, null);
    }

    private void addValueVariant(@NotNull String key,
                                 @SuppressWarnings("SameParameterValue") final @Nullable String description,
                                 final @Nullable String altText,
                                 @Nullable InsertHandler<LookupElement> handler) {
      String unquoted = StringUtil.unquoteString(key);
      LookupElementBuilder builder = LookupElementBuilder.create(!shouldWrapInQuotes(unquoted, true) ? unquoted : key);
      if (altText != null) {
        builder = builder.withPresentableText(altText);
      }
      if (description != null) {
        builder = builder.withTypeText(description);
      }
      if (handler != null) {
        builder = builder.withInsertHandler(handler);
      }
      myVariants.add(builder);
    }

    private boolean shouldWrapInQuotes(String key, boolean isValue) {
      return myWrapInQuotes && myWalker != null &&
             (isValue && myWalker.requiresValueQuotes()
                || !isValue && myWalker.requiresNameQuotes()
                || !myWalker.isValidIdentifier(key, myProject));
    }

    private void addPropertyVariant(@NotNull String key,
                                    @NotNull JsonSchemaObject jsonSchemaObject,
                                    boolean hasValue,
                                    boolean insertComma,
                                    @Nullable SchemaPath completionPath) {
      final Collection<JsonSchemaObject> variants = new JsonSchemaResolver(myProject, jsonSchemaObject).resolve();
      jsonSchemaObject = ObjectUtils.coalesce(ContainerUtil.getFirstItem(variants), jsonSchemaObject);
      key = !shouldWrapInQuotes(key, false) ? key : StringUtil.wrapWithDoubleQuote(key);
      LookupElementBuilder builder = LookupElementBuilder.create(key);

      final String typeText = JsonSchemaDocumentationProvider.getBestDocumentation(true, jsonSchemaObject);
      if (!StringUtil.isEmptyOrSpaces(typeText)) {
        final String text = StringUtil.removeHtmlTags(typeText);
        builder = builder.withTypeText(findFirstSentence(text), true);
      }
      else {
        String type = jsonSchemaObject.getTypeDescription(true);
        if (type != null) {
          builder = builder.withTypeText(type, true);
        }
      }

      builder = builder.withIcon(getIcon(jsonSchemaObject.guessType()));

      if (hasSameType(variants)) {
        final JsonSchemaType type = jsonSchemaObject.guessType();
        final List<Object> values = jsonSchemaObject.getEnum();
        Object defaultValue = jsonSchemaObject.getDefault();

        boolean hasValues = !ContainerUtil.isEmpty(values);
        if (type != null || hasValues || defaultValue != null) {
          builder = builder.withInsertHandler(
            !hasValues || values.stream().map(v -> v.getClass()).distinct().count() == 1 ?
            createPropertyInsertHandler(jsonSchemaObject, hasValue, insertComma) :
            createDefaultPropertyInsertHandler(true, insertComma));
        }
        else {
          builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(hasValue, insertComma));
        }
      }
      else {
        builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(hasValue, insertComma));
      }

      String deprecationMessage = jsonSchemaObject.getDeprecationMessage();
      if (deprecationMessage != null) {
        builder = builder.withTailText(JsonBundle.message("schema.documentation.deprecated.postfix"), true).withStrikeoutness(true);
      }

      myVariants.add(NestedCompletionsKt.prefixedBy(builder, completionPath, myWalker));
    }

    private static @NotNull String findFirstSentence(@NotNull String sentence) {
      int i = sentence.indexOf(". ");
      while (i >= 0) {
        String egText = ", e.g.";
        if (!sentence.regionMatches(i - egText.length() + 1, egText, 0, egText.length())) {
          return sentence.substring(0, i + 1);
        }
        i = sentence.indexOf(". ", i + 1);
      }
      return sentence;
    }

    private static @NotNull Icon getIcon(@Nullable JsonSchemaType type) {
      if (type == null) {
        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
      }
      return switch (type) {
        case _object -> AllIcons.Json.Object;
        case _array -> AllIcons.Json.Array;
        default -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
      };
    }

    private static boolean hasSameType(@NotNull Collection<JsonSchemaObject> variants) {
      return variants.stream().map(JsonSchemaObject::guessType).filter(Objects::nonNull).distinct().count() <= 1;
    }

    private static InsertHandler<LookupElement> createArrayOrObjectLiteralInsertHandler(boolean newline, int insertedTextSize) {
      return new InsertHandler<>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          Editor editor = context.getEditor();

          if (!newline) {
            EditorModificationUtil.moveCaretRelatively(editor, -1);
          }
          else {
            EditorModificationUtil.moveCaretRelatively(editor, -insertedTextSize);
            PsiDocumentManager.getInstance(context.getProject()).commitDocument(editor.getDocument());
            invokeEnterHandler(editor);
            EditorActionUtil.moveCaretToLineEnd(editor, false, false);
          }
          AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(editor, null);
        }
      };
    }

    private InsertHandler<LookupElement> createDefaultPropertyInsertHandler(@SuppressWarnings("SameParameterValue") boolean hasValue,
                                                                            boolean insertComma) {
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();

          if (handleInsideQuotesInsertion(context, editor, hasValue)) return;
          int offset = editor.getCaretModel().getOffset();
          int initialOffset = offset;
          CharSequence docChars = context.getDocument().getCharsSequence();
          while (offset < docChars.length() && Character.isWhitespace(docChars.charAt(offset))) {
            offset++;
          }
          if (hasValue) {
            // fix colon for YAML and alike
            if (offset < docChars.length() && docChars.charAt(offset) != ':') {
              editor.getDocument().insertString(initialOffset, ":");
              handleWhitespaceAfterColon(editor, docChars, initialOffset + 1);
            }
            return;
          }

          if (offset < docChars.length() && docChars.charAt(offset) == ':') {
            handleWhitespaceAfterColon(editor, docChars, offset + 1);
          }
          else {
            // inserting longer string for proper formatting
            final String stringToInsert = ": 1" + (insertComma ? "," : "");
            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);
            formatInsertedString(context, stringToInsert.length());
            offset = editor.getCaretModel().getOffset();
            context.getDocument().deleteString(offset, offset + 1);
          }
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
          AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
        }

        public void handleWhitespaceAfterColon(Editor editor, CharSequence docChars, int nextOffset) {
          if (nextOffset < docChars.length() && docChars.charAt(nextOffset) == ' ') {
            editor.getCaretModel().moveToOffset(nextOffset + 1);
          }
          else {
            editor.getCaretModel().moveToOffset(nextOffset);
            EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1);
          }
        }
      };
    }

    private @NotNull InsertHandler<LookupElement> createPropertyInsertHandler(@NotNull JsonSchemaObject jsonSchemaObject,
                                                                              final boolean hasValue,
                                                                              boolean insertComma) {
      JsonSchemaType type = jsonSchemaObject.guessType();
      List<Object> values = jsonSchemaObject.getEnum();
      if (type == null && values != null && !values.isEmpty()) type = detectType(values);
      final Object defaultValue = jsonSchemaObject.getDefault();
      final String defaultValueAsString = defaultValue == null || defaultValue instanceof JsonSchemaObject ? null :
                                          (defaultValue instanceof String ? "\"" + defaultValue + "\"" :
                                           String.valueOf(defaultValue));
      JsonSchemaType finalType = type;
      return new InsertHandler<>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();
          String stringToInsert = null;
          final String comma = insertComma ? "," : "";

          if (handleInsideQuotesInsertion(context, editor, hasValue)) return;

          PsiElement element = context.getFile().findElementAt(editor.getCaretModel().getOffset());
          boolean insertColon = element == null || !":".equals(element.getText());
          if (!insertColon) {
            editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + 1);
          }

          if (finalType != null) {
            boolean hadEnter;
            switch (finalType) {
              case _object -> {
                EditorModificationUtil.insertStringAtCaret(editor, insertColon ? ": " : " ",
                                                           false, true,
                                                           insertColon ? 2 : 1);
                hadEnter = false;
                boolean invokeEnter = myWalker.hasWhitespaceDelimitedCodeBlocks();
                if (insertColon && invokeEnter) {
                  invokeEnterHandler(editor);
                  hadEnter = true;
                }
                if (insertColon) {
                  stringToInsert = myWalker.getDefaultObjectValue() + comma;
                  EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                             false, true,
                                                             hadEnter ? 0 : 1);
                }

                if (hadEnter || !insertColon) {
                  EditorActionUtil.moveCaretToLineEnd(editor, false, false);
                }

                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                if (!hadEnter && stringToInsert != null) {
                  formatInsertedString(context, stringToInsert.length());
                }
                if (stringToInsert != null && !invokeEnter) {
                  invokeEnterHandler(editor);
                }
              }
              case _boolean -> {
                String value = String.valueOf(Boolean.TRUE.toString().equals(defaultValueAsString));
                stringToInsert = (insertColon ? ": " : " ") + value + comma;
                SelectionModel model = editor.getSelectionModel();

                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                           false, true,
                                                           stringToInsert.length() - comma.length());
                formatInsertedString(context, stringToInsert.length());
                int start = editor.getSelectionModel().getSelectionStart();
                model.setSelection(start - value.length(), start);
                AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
              }
              case _array -> {
                EditorModificationUtilEx.insertStringAtCaret(editor, insertColon ? ":" : " ",
                                                             false, true,
                                                             1);
                hadEnter = false;
                if (insertColon && myWalker.hasWhitespaceDelimitedCodeBlocks()) {
                  invokeEnterHandler(editor);
                  hadEnter = true;
                } else {
                  EditorModificationUtilEx.insertStringAtCaret(editor, " ", false, true, 1);
                }
                if (insertColon) {
                  stringToInsert = myWalker.getDefaultArrayValue() + comma;
                  EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                             false, true,
                                                             hadEnter ? 0 : 1);
                }
                if (hadEnter) {
                  EditorActionUtil.moveCaretToLineEnd(editor, false, false);
                }

                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

                if (stringToInsert != null && myWalker.requiresReformatAfterArrayInsertion()) {
                  formatInsertedString(context, stringToInsert.length());
                }
              }
              case _string, _integer, _number ->
                insertPropertyWithEnum(context, editor, defaultValueAsString, values, finalType, comma, myWalker, insertColon);
              default -> { }
            }
          }
          else {
            insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma, myWalker, insertColon);
          }
        }
      };
    }

    private static void invokeEnterHandler(Editor editor) {
      EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
      Caret caret = editor.getCaretModel().getCurrentCaret();
      handler.execute(editor, caret, CaretSpecificDataContext.create(
        DataManager.getInstance().getDataContext(editor.getContentComponent()), caret));
    }

    private boolean handleInsideQuotesInsertion(@NotNull InsertionContext context, @NotNull Editor editor, boolean hasValue) {
      if (myInsideStringLiteral) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = context.getFile().findElementAt(offset);
        int tailOffset = context.getTailOffset();
        int guessEndOffset = tailOffset + 1;
        if (element instanceof LeafPsiElement) {
          if (handleIncompleteString(editor, element)) return false;
          int endOffset = element.getTextRange().getEndOffset();
          if (endOffset > tailOffset) {
            context.getDocument().deleteString(tailOffset, endOffset - 1);
          }
        }
        if (hasValue) {
          return true;
        }
        editor.getCaretModel().moveToOffset(guessEndOffset);
      }
      else {
        editor.getCaretModel().moveToOffset(context.getTailOffset());
      }
      return false;
    }

    private static boolean handleIncompleteString(@NotNull Editor editor, @NotNull PsiElement element) {
      if (((LeafPsiElement)element).getElementType() == TokenType.WHITE_SPACE) {
        PsiElement prevSibling = element.getPrevSibling();
        if (prevSibling instanceof JsonProperty) {
          JsonValue nameElement = ((JsonProperty)prevSibling).getNameElement();
          if (!nameElement.getText().endsWith("\"")) {
            editor.getCaretModel().moveToOffset(nameElement.getTextRange().getEndOffset());
            EditorModificationUtil.insertStringAtCaret(editor, "\"", false, true, 1);
            return true;
          }
        }
      }
      return false;
    }

    private static @Nullable JsonSchemaType detectType(List<Object> values) {
      JsonSchemaType type = null;
      for (Object value : values) {
        JsonSchemaType newType = null;
        if (value instanceof Integer) newType = JsonSchemaType._integer;
        if (type != null && !type.equals(newType)) return null;
        type = newType;
      }
      return type;
    }
  }

  private static void insertPropertyWithEnum(InsertionContext context,
                                             Editor editor,
                                             String defaultValue,
                                             List<Object> values,
                                             JsonSchemaType type,
                                             String comma,
                                             JsonLikePsiWalker walker,
                                             boolean insertColon) {
    if (!walker.requiresValueQuotes() && defaultValue != null) {
      defaultValue = StringUtil.unquoteString(defaultValue);
    }
    final boolean isNumber = type != null && (JsonSchemaType._integer.equals(type) || JsonSchemaType._number.equals(type)) ||
                             type == null && (defaultValue != null &&
                                              !StringUtil.isQuotedString(defaultValue) ||
                                              values != null && ContainerUtil.and(values, v -> !(v instanceof String)));
    boolean hasValues = !ContainerUtil.isEmpty(values);
    boolean hasDefaultValue = !StringUtil.isEmpty(defaultValue);
    boolean hasQuotes = isNumber || !walker.requiresValueQuotes();
    int offset = editor.getCaretModel().getOffset();
    CharSequence charSequence = editor.getDocument().getCharsSequence();
    final String ws = charSequence.length() > offset && charSequence.charAt(offset) == ' ' ? "" : " ";
    final String colonWs = insertColon ? ":" + ws : ws;
    String stringToInsert = colonWs + (hasDefaultValue ? defaultValue : (hasQuotes ? "" : "\"\"")) + comma;
    EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                               insertColon ? 2 : 1);
    if (!hasQuotes || hasDefaultValue) {
      SelectionModel model = editor.getSelectionModel();
      int caretStart = model.getSelectionStart();
      int newOffset = caretStart + (hasDefaultValue ? defaultValue.length() : 1);
      if (hasDefaultValue && !hasQuotes) newOffset--;
      model.setSelection(hasQuotes ? caretStart : (caretStart + 1), newOffset);
      editor.getCaretModel().moveToOffset(newOffset);
    }

    if (!walker.hasWhitespaceDelimitedCodeBlocks() && !stringToInsert.equals(colonWs + comma)) {
      formatInsertedString(context, stringToInsert.length());
    }

    if (hasValues) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
  }

  public static void formatInsertedString(@NotNull InsertionContext context,
                                          int offset) {
    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformatText(context.getFile(), context.getStartOffset(), context.getTailOffset() + offset);
  }
}