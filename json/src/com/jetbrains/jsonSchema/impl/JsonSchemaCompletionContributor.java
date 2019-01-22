// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
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
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 10/1/2015.
 */
public class JsonSchemaCompletionContributor extends CompletionContributor {
  private static final String BUILTIN_USAGE_KEY = "json.schema.builtin.completion";
  private static final String SCHEMA_USAGE_KEY = "json.schema.schema.completion";
  private static final String USER_USAGE_KEY = "json.schema.user.completion";
  private static final String REMOTE_USAGE_KEY = "json.schema.remote.completion";

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final VirtualFile file = PsiUtilCore.getVirtualFile(position);
    if (file == null) return;

    final JsonSchemaService service = JsonSchemaService.Impl.get(position.getProject());
    if (!service.isApplicableToFile(file)) return;
    final JsonSchemaObject rootSchema = service.getSchemaObject(file);
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

    final VirtualFile schemaFile = rootSchema.getSchemaFile();
    updateStat(service.getSchemaProvider(schemaFile), schemaFile);
    doCompletion(parameters, result, rootSchema);
  }

  public static void doCompletion(@NotNull final CompletionParameters parameters,
                                  @NotNull final CompletionResultSet result,
                                  @NotNull final JsonSchemaObject rootSchema) {
    doCompletion(parameters, result, rootSchema, true);
  }

  public static void doCompletion(@NotNull final CompletionParameters parameters,
                                  @NotNull final CompletionResultSet result,
                                  @NotNull final JsonSchemaObject rootSchema,
                                  boolean stop) {
    final PsiElement completionPosition = parameters.getOriginalPosition() != null ? parameters.getOriginalPosition() :
                                          parameters.getPosition();
    new Worker(rootSchema, parameters.getPosition(), completionPosition, result).work();
    if (stop) {
      result.stopHere();
    }
  }

  @TestOnly
  @NotNull
  public static List<LookupElement> getCompletionVariants(@NotNull final JsonSchemaObject schema,
                                                          @NotNull final PsiElement position, @NotNull final PsiElement originalPosition) {
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
    switch (schemaType) {
      case schema:
        JsonSchemaUsageTriggerCollector.trigger(SCHEMA_USAGE_KEY);
        break;
      case userSchema:
        JsonSchemaUsageTriggerCollector.trigger(USER_USAGE_KEY);
        break;
      case embeddedSchema:
        JsonSchemaUsageTriggerCollector.trigger(BUILTIN_USAGE_KEY);
        break;
      case remoteSchema:
        // this works only for user-specified remote schemas in our settings, but not for auto-detected remote schemas
        JsonSchemaUsageTriggerCollector.trigger(REMOTE_USAGE_KEY);
        break;
    }
  }

  private static class Worker {
    @NotNull private final JsonSchemaObject myRootSchema;
    @NotNull private final PsiElement myPosition;
    @NotNull private final PsiElement myOriginalPosition;
    @NotNull private final Consumer<LookupElement> myResultConsumer;
    private final boolean myWrapInQuotes;
    private final boolean myInsideStringLiteral;
    // we need this set to filter same-named suggestions (they can be suggested by several matching schemes)
    private final Set<LookupElement> myVariants;
    private final JsonLikePsiWalker myWalker;

    Worker(@NotNull JsonSchemaObject rootSchema, @NotNull PsiElement position,
           @NotNull PsiElement originalPosition, @NotNull final Consumer<LookupElement> resultConsumer) {
      myRootSchema = rootSchema;
      myPosition = position;
      myOriginalPosition = originalPosition;
      myResultConsumer = resultConsumer;
      myVariants = new HashSet<>();
      myWalker = JsonLikePsiWalker.getWalker(myPosition, myRootSchema);
      myWrapInQuotes = myWalker != null && myWalker.requiresNameQuotes() && !(position.getParent() instanceof JsonStringLiteral);
      myInsideStringLiteral = position.getParent() instanceof JsonStringLiteral;
    }

    public void work() {
      if (myWalker == null) return;
      final PsiElement checkable = myWalker.findElementToCheck(myPosition);
      if (checkable == null) return;
      final ThreeState isName = myWalker.isName(checkable);
      final JsonPointerPosition position = myWalker.findPosition(checkable, isName == ThreeState.NO);
      if (position == null || position.isEmpty() && isName == ThreeState.NO) return;

      final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(myRootSchema, false, position).resolve();
      final Set<String> knownNames = ContainerUtil.newHashSet();
      // too long here, refactor further
      schemas.forEach(schema -> {
        if (isName != ThreeState.NO) {
          final boolean insertComma = myWalker.hasMissingCommaAfter(myPosition);
          final boolean hasValue = myWalker.isPropertyWithValue(checkable);

          final Collection<String> properties = myWalker.getPropertyNamesOfParentObject(myOriginalPosition, myPosition);
          final JsonPropertyAdapter adapter = myWalker.getParentPropertyAdapter(myOriginalPosition);

          final Map<String, JsonSchemaObject> schemaProperties = schema.getProperties();
          addAllPropertyVariants(insertComma, hasValue, properties, adapter, schemaProperties, knownNames);
          addIfThenElsePropertyNameVariants(schema, insertComma, hasValue, properties, adapter, knownNames);
        }

        if (isName != ThreeState.YES) {
          suggestValues(schema, isName == ThreeState.NO);
        }
      });

      for (LookupElement variant : myVariants) {
        myResultConsumer.consume(variant);
      }
    }

    private void addIfThenElsePropertyNameVariants(@NotNull JsonSchemaObject schema,
                                                   boolean insertComma,
                                                   boolean hasValue,
                                                   @NotNull Collection<String> properties,
                                                   @Nullable JsonPropertyAdapter adapter,
                                                   Set<String> knownNames) {
      if (schema.getIf() == null) return;

      JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(myPosition, schema);
      JsonPropertyAdapter propertyAdapter = walker == null ? null : walker.getParentPropertyAdapter(myPosition);
      if (propertyAdapter == null) return;

      JsonObjectValueAdapter object = propertyAdapter.getParentObject();
      if (object == null) return;

      JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(JsonComplianceCheckerOptions.RELAX_ENUM_CHECK);
      checker.checkByScheme(object, schema.getIf());
      if (checker.isCorrect()) {
        JsonSchemaObject then = schema.getThen();
        if (then != null) {
          addAllPropertyVariants(insertComma, hasValue, properties, adapter, then.getProperties(), knownNames);
        }
      }
      else {
        JsonSchemaObject schemaElse = schema.getElse();
        if (schemaElse != null) {
          addAllPropertyVariants(insertComma, hasValue, properties, adapter, schemaElse.getProperties(), knownNames);
        }
      }
    }

    private void addAllPropertyVariants(boolean insertComma,
                                        boolean hasValue,
                                        Collection<String> properties,
                                        JsonPropertyAdapter adapter,
                                        Map<String, JsonSchemaObject> schemaProperties, Set<String> knownNames) {
      schemaProperties.keySet().stream()
        .filter(name -> !properties.contains(name) && !knownNames.contains(name) || adapter != null && name.equals(adapter.getName()))
        .forEach(name -> {
          knownNames.add(name);
          addPropertyVariant(name, schemaProperties.get(name), hasValue, insertComma);
        });
    }

    // some schemas provide empty array / empty object in enum values...
    private static final Set<String> filtered = ContainerUtil.set("[]", "{}", "[ ]", "{ }");

    private void suggestValues(JsonSchemaObject schema, boolean isSurelyValue) {
      suggestValuesForSchemaVariants(schema.getAnyOf(), isSurelyValue);
      suggestValuesForSchemaVariants(schema.getOneOf(), isSurelyValue);
      suggestValuesForSchemaVariants(schema.getAllOf(), isSurelyValue);

      if (schema.getEnum() != null) {
        for (Object o : schema.getEnum()) {
          if (myInsideStringLiteral && !(o instanceof String)) continue;
          String variant = o.toString();
          if (!filtered.contains(variant)) {
            addValueVariant(variant, null);
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
        if (propertyAdapter == null || !"required".equals(propertyAdapter.getName())) return;
        PsiElement checkable = myWalker.findElementToCheck(myPosition);
        if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) return;
        JsonObject propertiesObject = JsonRequiredPropsReferenceProvider.findPropertiesObject(checkable);
        if (propertiesObject == null) return;
        PsiElement parent = checkable.getParent();
        Set<String> items = parent instanceof JsonArray
                            ? ((JsonArray)parent).getValueList().stream()
                              .filter(v -> v instanceof JsonStringLiteral).map(v -> ((JsonStringLiteral)v).getValue())
                              .collect(Collectors.toSet())
                            : ContainerUtil.newHashSet();
        propertiesObject.getPropertyList().stream().map(p -> p.getName()).filter(n -> !items.contains(n)).forEach(n -> addStringVariant(n));
      }
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
                        "[...]", createArrayOrObjectLiteralInsertHandler(myWalker.hasWhitespaceDelimitedCodeBlocks(), value.length()));
      }
      else if (JsonSchemaType._object.equals(type)) {
        String value = myWalker.getDefaultObjectValue();
        addValueVariant(value, null,
                        "{...}", createArrayOrObjectLiteralInsertHandler(myWalker.hasWhitespaceDelimitedCodeBlocks(), value.length()));
      }
    }

    private void addPossibleStringValue(JsonSchemaObject schema) {
      Object defaultValue = schema.getDefault();
      String defaultValueString = defaultValue == null ? null : defaultValue.toString();
      addStringVariant(defaultValueString);
    }

    private void addStringVariant(String defaultValueString) {
      if (!StringUtil.isEmpty(defaultValueString)) {
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


    private void addValueVariant(@NotNull String key, @SuppressWarnings("SameParameterValue") @Nullable final String description) {
      addValueVariant(key, description, null, null);
    }

    private void addValueVariant(@NotNull String key,
                                 @SuppressWarnings("SameParameterValue") @Nullable final String description,
                                 @Nullable final String altText,
                                 @Nullable InsertHandler<LookupElement> handler) {
      LookupElementBuilder builder = LookupElementBuilder.create(!myWrapInQuotes ? StringUtil.unquoteString(key) : key);
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

    private void addPropertyVariant(@NotNull String key,
                                    @NotNull JsonSchemaObject jsonSchemaObject,
                                    boolean hasValue,
                                    boolean insertComma) {
      final Collection<JsonSchemaObject> variants = new JsonSchemaResolver(jsonSchemaObject).resolve();
      jsonSchemaObject = ObjectUtils.coalesce(ContainerUtil.getFirstItem(variants), jsonSchemaObject);
      key = !myWrapInQuotes ? key : StringUtil.wrapWithDoubleQuote(key);
      LookupElementBuilder builder = LookupElementBuilder.create(key);

      final String typeText = JsonSchemaDocumentationProvider.getBestDocumentation(true, jsonSchemaObject);
      if (!StringUtil.isEmptyOrSpaces(typeText)) {
        final String text = StringUtil.removeHtmlTags(typeText);
        final int firstSentenceMark = text.indexOf(". ");
        builder = builder.withTypeText(firstSentenceMark == -1 ? text : text.substring(0, firstSentenceMark + 1), true);
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
      else if (!hasValue) {
        builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(false, insertComma));
      }

      myVariants.add(builder);
    }

    @NotNull
    private static Icon getIcon(@Nullable JsonSchemaType type) {
      if (type == null) return AllIcons.Nodes.Property;
      switch (type) {
        case _object:
          return AllIcons.Json.Object;
        case _array:
          return AllIcons.Json.Array;
        default:
          return AllIcons.Nodes.Property;
      }
    }

    private static boolean hasSameType(@NotNull Collection<JsonSchemaObject> variants) {
      return variants.stream().map(JsonSchemaObject::guessType).filter(Objects::nonNull).distinct().count() <= 1;
    }

    private static InsertHandler<LookupElement> createArrayOrObjectLiteralInsertHandler(boolean newline, int insertedTextSize) {
      return new InsertHandler<LookupElement>() {
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

          // inserting longer string for proper formatting
          final String stringToInsert = ": 1" + (insertComma ? "," : "");
          EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);
          formatInsertedString(context, stringToInsert.length());
          final int offset = editor.getCaretModel().getOffset();
          context.getDocument().deleteString(offset, offset + 1);
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
          AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
        }
      };
    }

    @NotNull
    private InsertHandler<LookupElement> createPropertyInsertHandler(@NotNull JsonSchemaObject jsonSchemaObject,
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
      return new InsertHandler<LookupElement>() {
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
              case _object:
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
                break;
              case _boolean:
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
                break;
              case _array:
                EditorModificationUtil.insertStringAtCaret(editor, insertColon ? ": " : " ",
                                                           false, true,
                                                           insertColon ? 2 : 1);
                hadEnter = false;
                if (insertColon && myWalker.hasWhitespaceDelimitedCodeBlocks()) {
                  invokeEnterHandler(editor);
                  hadEnter = true;
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

                if (stringToInsert != null) {
                  formatInsertedString(context, stringToInsert.length());
                }
                break;
              case _string:
              case _integer:
                insertPropertyWithEnum(context, editor, defaultValueAsString, values, finalType, comma, myWalker, insertColon);
                break;
              default:
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
      handler.execute(editor, caret,
                      new CaretSpecificDataContext(DataManager.getInstance().getDataContext(editor.getContentComponent()), caret));
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

    @Nullable
    private static JsonSchemaType detectType(List<Object> values) {
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
    final String colonWs = insertColon ? ": " : " ";
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