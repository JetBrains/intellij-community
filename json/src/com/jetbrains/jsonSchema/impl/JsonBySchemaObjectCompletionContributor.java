package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Irina.Chernushina on 10/1/2015.
 */
class JsonBySchemaObjectCompletionContributor extends CompletionContributor {
  private static final String BUILTIN_USAGE_KEY = "json.schema.builtin.completion";
  private static final String SCHEMA_USAGE_KEY = "json.schema.schema.completion";
  private static final String USER_USAGE_KEY = "json.schema.user.completion";
  @NotNull private final SchemaType myType;
  @NotNull private final JsonSchemaObject myRootSchema;

  public JsonBySchemaObjectCompletionContributor(@NotNull SchemaType type, final @NotNull JsonSchemaObject rootSchema) {
    myType = type;
    myRootSchema = rootSchema;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final PsiFile containingFile = position.getContainingFile();
    if (containingFile == null) return;

    updateStat();
    new Worker(myRootSchema, position, result).work();
    result.stopHere();
  }

  public static List<LookupElement> getCompletionVariants(@NotNull final JsonSchemaObject schema, @NotNull final PsiElement position) {
    final List<LookupElement> result = new ArrayList<>();
    new Worker(schema, position, element -> result.add(element)).work();
    return result;
  }

  private void updateStat() {
    if (SchemaType.schema.equals(myType)) {
      UsageTrigger.trigger(SCHEMA_USAGE_KEY);
    } else if (SchemaType.embeddedSchema.equals(myType)) {
      UsageTrigger.trigger(BUILTIN_USAGE_KEY);
    } else if (SchemaType.userSchema.equals(myType)) {
      UsageTrigger.trigger(USER_USAGE_KEY);
    }
  }

  private static class Worker {
    @NotNull private final JsonSchemaObject myRootSchema;
    @NotNull private final PsiElement myPosition;
    @NotNull private final Consumer<LookupElement> myResultConsumer;
    private final boolean myInsideStringLiteral;
    private final List<LookupElement> myVariants;

    public Worker(@NotNull JsonSchemaObject rootSchema, @NotNull PsiElement position,
                  @NotNull final Consumer<LookupElement> resultConsumer) {
      myRootSchema = rootSchema;
      myPosition = position;
      myResultConsumer = resultConsumer;
      myInsideStringLiteral = position.getParent() instanceof JsonStringLiteral;
      myVariants = new ArrayList<>();
    }

    public void work() {
      JsonSchemaWalker.findSchemasForCompletion(myPosition, new JsonSchemaWalker.CompletionSchemesConsumer() {
        @Override
        public void consume(boolean isName, @NotNull JsonSchemaObject schema) {
          if (isName) {
            PsiElement possibleParent = myPosition.getParent().getParent();
            final JsonProperty parent = possibleParent instanceof JsonProperty ? (JsonProperty)possibleParent : null;
            final boolean hasValue = hasValuePart(parent);

            final Collection<String> properties = getExistingProperties(parent);

            JsonSchemaPropertyProcessor.process(new JsonSchemaPropertyProcessor.PropertyProcessor() {
              @Override
              public boolean process(String name, JsonSchemaObject schema) {
                if (properties.contains(name)) {
                  return true;
                }

                addPropertyVariant(name, schema, hasValue);
                return true;
              }
            }, schema);
          }
          else {
            suggestValues(schema);
          }
        }
      }, myRootSchema);
      for (LookupElement variant : myVariants) {
        myResultConsumer.consume(variant);
      }
    }

    public Collection<String> getExistingProperties(@Nullable JsonProperty property) {
      if (property == null) return ContainerUtil.emptyList();

      PsiElement parent = property.getParent();
      if (!(parent instanceof JsonObject)) return ContainerUtil.emptyList();

      JsonObject object = (JsonObject)parent;
      HashSet<String> result = ContainerUtil.newHashSet();
      for (JsonProperty jsonProperty : object.getPropertyList()) {
        if (jsonProperty == property) continue;

        result.add(jsonProperty.getName());
      }

      return result;
    }

    public boolean hasValuePart(@Nullable JsonProperty property) {
      if (property != null && myInsideStringLiteral) {
        return property.getValue() != null;
      }
      return true;
    }

    private void suggestValues(JsonSchemaObject schema) {
      suggestValuesForSchemaVariants(schema.getAnyOf());
      suggestValuesForSchemaVariants(schema.getOneOf());
      suggestValuesForSchemaVariants(schema.getAllOf());

      if (schema.getEnum() != null) {
        //myVariants.clear();
        for (Object o : schema.getEnum()) {
          addValueVariant(o.toString(), null);
        }
      }
      else {
        final JsonSchemaType type = schema.getType();
        if (JsonSchemaType._boolean.equals(type)) {
          addPossibleBooleanValue(type);
          final List<JsonSchemaType> variants = schema.getTypeVariants();
          if (variants != null) {
            for (JsonSchemaType variant : variants) {
              addPossibleBooleanValue(variant);
            }
          }
        } else if (JsonSchemaType._string.equals(type)) {
          addPossibleStringValue(schema);
        }
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


    private void addValueVariant(@NotNull String key, @Nullable final String description) {
      LookupElementBuilder builder = LookupElementBuilder.create(myInsideStringLiteral ? StringUtil.unquoteString(key) : key);
      if (description != null) {
        builder = builder.withTypeText(description);
      }
      myVariants.add(builder);
    }

    private void addPropertyVariant(@NotNull String key, @NotNull JsonSchemaObject jsonSchemaObject, boolean hasValue) {
      final String description = jsonSchemaObject.getDescription();
      final String title = jsonSchemaObject.getTitle();
      key = myInsideStringLiteral ? key : StringUtil.wrapWithDoubleQuote(key);
      LookupElementBuilder builder = LookupElementBuilder.create(key);

      String typeText = StringUtil.isEmpty(title) ? description : title;
      if (!StringUtil.isEmpty(typeText)) {
        builder = builder.withTypeText(typeText, true);
      }

      final JsonSchemaType type = jsonSchemaObject.getType();
      final List<Object> values = jsonSchemaObject.getEnum();
      if (type != null || !ContainerUtil.isEmpty(values)) {
        builder = builder.withInsertHandler(createPropertyInsertHandler(jsonSchemaObject, hasValue));
      }

      myVariants.add(builder);
    }

    @NotNull
    private InsertHandler<LookupElement> createPropertyInsertHandler(@NotNull JsonSchemaObject jsonSchemaObject, final boolean hasValue) {
      final JsonSchemaType type = jsonSchemaObject.getType();
      final List<Object> values = jsonSchemaObject.getEnum();
      final Object defaultValue = jsonSchemaObject.getDefault();
      final String defaultValueAsString = defaultValue == null ? null : String.valueOf(defaultValue);
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();
          String stringToInsert;


          if (myInsideStringLiteral) {
            int offset = editor.getCaretModel().getOffset();
            PsiElement element = context.getFile().findElementAt(offset);
            int tailOffset = context.getTailOffset();
            int guessEndOffset = tailOffset + 1;
            if (element != null) {
              int endOffset = element.getTextRange().getEndOffset();
              if (endOffset > tailOffset) {
                context.getDocument().deleteString(tailOffset, endOffset - 1);
              }
              //move caret out of quotes

            }
            if (hasValue) {
              return;
            }
            editor.getCaretModel().moveToOffset(guessEndOffset);
          }


          if (type != null) {
            switch (type) {
              case _object:
                stringToInsert = ":{}";
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);

                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                formatInsertedString(context, project, stringToInsert.length());
                EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
                handler.execute(editor, editor.getCaretModel().getCurrentCaret(),
                                DataManager.getInstance().getDataContext(editor.getContentComponent()));
                break;
              case _boolean:
                String value = String.valueOf(Boolean.TRUE.toString().equals(defaultValueAsString));
                stringToInsert = ":" + value;
                SelectionModel model = editor.getSelectionModel();

                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, stringToInsert.length());
                formatInsertedString(context, context.getProject(), stringToInsert.length());
                int start = editor.getSelectionModel().getSelectionStart();
                model.setSelection(start - value.length(), start);
                AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
                break;
              case _array:
                stringToInsert = ":[]";
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);

                formatInsertedString(context, project, stringToInsert.length());
                break;
              case _string:
                insertStringPropertyWithEnum(context, editor, defaultValueAsString, values);
                break;
              default:
            }
          }
          else {
            insertStringPropertyWithEnum(context, editor, defaultValueAsString, values);
          }
        }
      };
    }
  }

  public static void insertStringPropertyWithEnum(InsertionContext context, Editor editor, String defaultValue, List<Object> values) {
    String start = ":\"";
    String end = "\"";
    boolean hasValues = !ContainerUtil.isEmpty(values);
    boolean hasDefaultValue = !StringUtil.isEmpty(defaultValue);
    String stringToInsert = start + (hasDefaultValue ? defaultValue : "") + end;
    EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);
    if (hasDefaultValue) {
      SelectionModel model = editor.getSelectionModel();
      int caretStart = model.getSelectionStart();
      int newOffset = caretStart + defaultValue.length();
      model.setSelection(caretStart, newOffset);
      editor.getCaretModel().moveToOffset(newOffset);
    }

    formatInsertedString(context, context.getProject(), stringToInsert.length());

    if (hasValues) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
  }

  public static void formatInsertedString(InsertionContext context, Project project, int offset) {
    PsiDocumentManager.getInstance(project).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformatText(context.getFile(), context.getStartOffset(), context.getTailOffset() + offset);
  }
}