// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
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

    updateStat(service.getSchemaProvider(rootSchema.getSchemaFile()));
    doCompletion(parameters, result, rootSchema);
  }

  public static void doCompletion(@NotNull final CompletionParameters parameters,
                                  @NotNull final CompletionResultSet result,
                                  @NotNull final JsonSchemaObject rootSchema) {
    final PsiElement completionPosition = parameters.getOriginalPosition() != null ? parameters.getOriginalPosition() :
                                          parameters.getPosition();
    new Worker(rootSchema, parameters.getPosition(), completionPosition, result).work();
    result.stopHere();
  }

  @TestOnly
  public static List<LookupElement> getCompletionVariants(@NotNull final JsonSchemaObject schema,
                                                          @NotNull final PsiElement position, @NotNull final PsiElement originalPosition) {
    final List<LookupElement> result = new ArrayList<>();
    new Worker(schema, position, originalPosition, element -> result.add(element)).work();
    return result;
  }

  private static void updateStat(@Nullable JsonSchemaFileProvider provider) {
    if (provider == null) return;
    final SchemaType schemaType = provider.getSchemaType();
    FUSApplicationUsageTrigger usageTrigger = FUSApplicationUsageTrigger.getInstance();
    switch (schemaType) {
      case schema:
        usageTrigger.trigger(JsonSchemaUsageTriggerCollector.class, SCHEMA_USAGE_KEY);
        break;
      case userSchema:
        usageTrigger.trigger(JsonSchemaUsageTriggerCollector.class, USER_USAGE_KEY);
        break;
      case embeddedSchema:
        usageTrigger.trigger(JsonSchemaUsageTriggerCollector.class, BUILTIN_USAGE_KEY);
        break;
      case remoteSchema:
        usageTrigger.trigger(JsonSchemaUsageTriggerCollector.class, REMOTE_USAGE_KEY);
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

    public Worker(@NotNull JsonSchemaObject rootSchema, @NotNull PsiElement position,
                  @NotNull PsiElement originalPosition, @NotNull final Consumer<LookupElement> resultConsumer) {
      myRootSchema = rootSchema;
      myPosition = position;
      myOriginalPosition = originalPosition;
      myResultConsumer = resultConsumer;
      myVariants = new HashSet<>();
      myWalker = JsonLikePsiWalker.getWalker(myPosition, myRootSchema);
      myWrapInQuotes = myWalker != null && myWalker.isNameQuoted() && !(position.getParent() instanceof JsonStringLiteral);
      myInsideStringLiteral = position.getParent() instanceof JsonStringLiteral;
    }

    public void work() {
      if (myWalker == null) return;
      final PsiElement checkable = myWalker.goUpToCheckable(myPosition);
      if (checkable == null) return;
      final boolean isName = myWalker.isName(checkable);
      final List<JsonSchemaVariantsTreeBuilder.Step> position = myWalker.findPosition(checkable, !isName);
      if (position == null || position.isEmpty() && !isName) return;

      final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(myRootSchema, false, position).resolve();
      // too long here, refactor further
      schemas.forEach(schema -> {
        if (isName) {
          final boolean insertComma = myWalker.hasPropertiesBehindAndNoComma(myPosition);
          final boolean hasValue = myWalker.isPropertyWithValue(myPosition.getParent().getParent());

          final Collection<String> properties = myWalker.getPropertyNamesOfParentObject(myOriginalPosition);
          final JsonPropertyAdapter adapter = myWalker.getParentPropertyAdapter(myOriginalPosition);

          final Map<String, JsonSchemaObject> schemaProperties = schema.getProperties();
          addAllPropertyVariants(insertComma, hasValue, properties, adapter, schemaProperties);
          addIfThenElsePropertyNameVariants(schema, insertComma, hasValue, properties, adapter);
        }
        else {
          suggestValues(schema);
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
                                                   @Nullable JsonPropertyAdapter adapter) {
      if (schema.getIf() == null) return;

      JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(myPosition, schema);
      JsonPropertyAdapter propertyAdapter = walker == null ? null : walker.getParentPropertyAdapter(myPosition);
      if (propertyAdapter == null) return;

      JsonObjectValueAdapter object = propertyAdapter.getParentObject();
      if (object == null) return;

      JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker();
      checker.checkByScheme(object, schema.getIf());
      if (checker.isCorrect()) {
        JsonSchemaObject then = schema.getThen();
        if (then != null) {
          addAllPropertyVariants(insertComma, hasValue, properties, adapter, then.getProperties());
        }
      }
      else {
        JsonSchemaObject schemaElse = schema.getElse();
        if (schemaElse != null) {
          addAllPropertyVariants(insertComma, hasValue, properties, adapter, schemaElse.getProperties());
        }
      }
    }

    private void addAllPropertyVariants(boolean insertComma,
                                        boolean hasValue,
                                        Collection<String> properties,
                                        JsonPropertyAdapter adapter,
                                        Map<String, JsonSchemaObject> schemaProperties) {
      schemaProperties.keySet().stream()
        .filter(name -> !properties.contains(name) || adapter != null && name.equals(adapter.getName()))
        .forEach(name -> addPropertyVariant(name, schemaProperties.get(name), hasValue, insertComma));
    }

    private void suggestValues(JsonSchemaObject schema) {
      suggestValuesForSchemaVariants(schema.getAnyOf());
      suggestValuesForSchemaVariants(schema.getOneOf());
      suggestValuesForSchemaVariants(schema.getAllOf());

      if (schema.getEnum() != null) {
        for (Object o : schema.getEnum()) {
          addValueVariant(o.toString(), null);
        }
      }
      else {
        final JsonSchemaType type = schema.getType();
        if (type != null) {
          suggestByType(schema, type);
        } else if (schema.getTypeVariants() != null) {
          for (JsonSchemaType schemaType : schema.getTypeVariants()) {
            suggestByType(schema, schemaType);
          }
        }
      }
    }

    private void suggestByType(JsonSchemaObject schema, JsonSchemaType type) {
      if (JsonSchemaType._string.equals(type)) {
        addPossibleStringValue(schema);
      }
      if (myInsideStringLiteral){
        return;
      }
      if (JsonSchemaType._boolean.equals(type)) {
        addPossibleBooleanValue(type);
      } else if (JsonSchemaType._null.equals(type)) {
        addValueVariant("null", null);
      } else if (JsonSchemaType._array.equals(type)) {
        addValueVariant("[]", null, createArrayOrObjectLiteralInsertHandler());
      } else if (JsonSchemaType._object.equals(type)) {
        addValueVariant("{}", null, createArrayOrObjectLiteralInsertHandler());
      }
    }

    private void addPossibleStringValue(JsonSchemaObject schema) {
      Object defaultValue = schema.getDefault();
      String defaultValueString = defaultValue == null ? null : defaultValue.toString();
      if (!StringUtil.isEmpty(defaultValueString)) {
        String quotedValue = defaultValueString;
        if (!StringUtil.isQuotedString(quotedValue)) {
          quotedValue = StringUtil.wrapWithDoubleQuote(quotedValue);
        }
        addValueVariant(quotedValue, null);
      }
    }

    private void suggestValuesForSchemaVariants(List<JsonSchemaObject> list) {
      if (list != null && list.size() > 0) {
        for (JsonSchemaObject schemaObject : list) {
          suggestValues(schemaObject);
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
      addValueVariant(key, description, null);
    }

    private void addValueVariant(@NotNull String key, @SuppressWarnings("SameParameterValue") @Nullable final String description,
                                 @Nullable InsertHandler<LookupElement> handler) {
      LookupElementBuilder builder = LookupElementBuilder.create(!myWrapInQuotes ? StringUtil.unquoteString(key) : key);
      if (description != null) {
        builder = builder.withTypeText(description);
      }
      if (handler != null) {
        builder = builder.withInsertHandler(handler);
      }
      myVariants.add(builder);
    }

    private void addPropertyVariant(@NotNull String key, @NotNull JsonSchemaObject jsonSchemaObject, boolean hasValue, boolean insertComma) {
      final Collection<JsonSchemaObject> variants = new JsonSchemaResolver(jsonSchemaObject).resolve();
      jsonSchemaObject = ObjectUtils.coalesce(ContainerUtil.getFirstItem(variants), jsonSchemaObject);
      key = !myWrapInQuotes ? key : StringUtil.wrapWithDoubleQuote(key);
      LookupElementBuilder builder = LookupElementBuilder.create(key);

      final String typeText = JsonSchemaDocumentationProvider.getBestDocumentation(true, jsonSchemaObject);
      if (!StringUtil.isEmptyOrSpaces(typeText)) {
        builder = builder.withTypeText(StringUtil.removeHtmlTags(typeText), true);
      }

      builder = builder.withIcon(getIcon(jsonSchemaObject.getType()));

      if (hasSameType(variants)) {
        final JsonSchemaType type = jsonSchemaObject.getType();
        final List<Object> values = jsonSchemaObject.getEnum();
        if (type != null || !ContainerUtil.isEmpty(values) || jsonSchemaObject.getDefault() != null) {
          builder = builder.withInsertHandler(createPropertyInsertHandler(jsonSchemaObject, hasValue, insertComma));
        }
      } else if (!hasValue) {
        builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(false, insertComma));
      }

      myVariants.add(builder);
    }

    @NotNull
    private static Icon getIcon(@Nullable JsonSchemaType type) {
      if (type == null) return AllIcons.Nodes.Property;
      switch (type) {
        case _object:
          return AllIcons.Json.Property_braces;
        case _array:
          return AllIcons.Json.Property_brackets;
        default:
          return AllIcons.Nodes.Property;
      }
    }

    private static boolean hasSameType(@NotNull Collection<JsonSchemaObject> variants) {
      return variants.stream().map(JsonSchemaObject::getType).filter(Objects::nonNull).distinct().count() <= 1;
    }

    private static InsertHandler<LookupElement> createArrayOrObjectLiteralInsertHandler() {
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          EditorModificationUtil.moveCaretRelatively(context.getEditor(), -1);
          AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
        }
      };
    }

    private InsertHandler<LookupElement> createDefaultPropertyInsertHandler(@SuppressWarnings("SameParameterValue") boolean hasValue,
                                                                            boolean insertComma) {
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
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
      JsonSchemaType type = jsonSchemaObject.getType();
      final List<Object> values = jsonSchemaObject.getEnum();
      if (type == null && values != null && !values.isEmpty()) type = detectType(values);
      final Object defaultValue = jsonSchemaObject.getDefault();
      final String defaultValueAsString = defaultValue == null || defaultValue instanceof JsonSchemaObject ? null :
                                          (defaultValue instanceof String ? "\"" + defaultValue + "\"" :
                                                                        String.valueOf(defaultValue));
      JsonSchemaType finalType = type;
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();
          String stringToInsert;
          final String comma = insertComma ? "," : "";

          if (handleInsideQuotesInsertion(context, editor, hasValue)) return;

          if (finalType != null) {
            switch (finalType) {
              case _object:
                stringToInsert = ":{}" + comma;
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);

                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                formatInsertedString(context, stringToInsert.length());
                EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
                handler.execute(editor, editor.getCaretModel().getCurrentCaret(),
                                DataManager.getInstance().getDataContext(editor.getContentComponent()));
                break;
              case _boolean:
                String value = String.valueOf(Boolean.TRUE.toString().equals(defaultValueAsString));
                stringToInsert = ":" + value + comma;
                SelectionModel model = editor.getSelectionModel();

                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, stringToInsert.length() - comma.length());
                formatInsertedString(context, stringToInsert.length());
                int start = editor.getSelectionModel().getSelectionStart();
                model.setSelection(start - value.length(), start);
                AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
                break;
              case _array:
                stringToInsert = ":[]" + comma;
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);

                formatInsertedString(context, stringToInsert.length());
                break;
              case _string:
              case _integer:
                insertPropertyWithEnum(context, editor, defaultValueAsString, values, finalType, comma);
                break;
              default:
            }
          }
          else {
            insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma);
          }
        }
      };
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
      } else editor.getCaretModel().moveToOffset(context.getTailOffset());
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

  public static void insertPropertyWithEnum(InsertionContext context,
                                            Editor editor,
                                            String defaultValue,
                                            List<Object> values,
                                            JsonSchemaType type, String comma) {
    final boolean isNumber = type != null && (JsonSchemaType._integer.equals(type) || JsonSchemaType._number.equals(type)) ||
      type == null && (defaultValue != null &&
                       !StringUtil.isQuotedString(defaultValue) || values != null && ContainerUtil.and(values, v -> !(v instanceof String)));
    boolean hasValues = !ContainerUtil.isEmpty(values);
    boolean hasDefaultValue = !StringUtil.isEmpty(defaultValue);
    String stringToInsert = ":" + (hasDefaultValue ? defaultValue : (isNumber ? "" : "\"\"")) + comma;
    EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 1);
    if (!isNumber || hasDefaultValue) {
      SelectionModel model = editor.getSelectionModel();
      int caretStart = model.getSelectionStart();
      int newOffset = caretStart + (hasDefaultValue ? defaultValue.length() : 1);
      if (hasDefaultValue && !isNumber) newOffset--;
      model.setSelection(isNumber ? caretStart : (caretStart + 1), newOffset);
      editor.getCaretModel().moveToOffset(newOffset);
    }

    formatInsertedString(context, stringToInsert.length());

    if (hasValues) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
  }

  public static void formatInsertedString(@NotNull InsertionContext context, int offset) {
    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformatText(context.getFile(), context.getStartOffset(), context.getTailOffset() + offset);
  }
}