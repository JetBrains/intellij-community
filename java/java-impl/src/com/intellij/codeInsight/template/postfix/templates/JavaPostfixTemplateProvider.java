// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.editable.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import kotlin.LazyKt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;


public class JavaPostfixTemplateProvider implements PostfixTemplateProvider {
  private static final String LANGUAGE_LEVEL_ATTR = "language-level";
  private static final String CONDITIONS_TAG = "conditions";
  private static final String TEMPLATE_TAG = "template";
  private static final String CONDITION_TAG = "condition";
  private static final String ID_ATTR = "id";
  private static final String FQN_ATTR = "fqn";
  private static final String TOPMOST_ATTR = "topmost";

  private final Set<PostfixTemplate> myBuiltinTemplates = ContainerUtil.newHashSet(
    new AssertStatementPostfixTemplate(this),
    new SynchronizedStatementPostfixTemplate(this),
    new ForAscendingPostfixTemplate(this),
    new ForDescendingPostfixTemplate(this),
    new WhileStatementPostfixTemplate(this),
    new SoutPostfixTemplate(this),
    new ReturnStatementPostfixTemplate(this),
    new OptionalPostfixTemplate(this),
    new ForeachPostfixTemplate("iter", this),
    new ForeachPostfixTemplate("for", this),
    new LambdaPostfixTemplate(this),
    new ThrowExceptionPostfixTemplate(this),
    new FormatPostfixTemplate(this),
    new ObjectsRequireNonNullPostfixTemplate(this),
    new ArgumentPostfixTemplate(this),

    new CastExpressionPostfixTemplate(),
    new ElseStatementPostfixTemplate(),
    new IfStatementPostfixTemplate(),
    new InstanceofExpressionPostfixTemplate(),
    new InstanceofExpressionPostfixTemplate("inst"),
    new IntroduceFieldPostfixTemplate(),
    new IntroduceVariablePostfixTemplate(),
    new IsNullCheckPostfixTemplate(),
    new NotExpressionPostfixTemplate(),
    new NotExpressionPostfixTemplate("!"),
    new NotNullCheckPostfixTemplate(),
    new NotNullCheckPostfixTemplate("nn"),
    new ParenthesizedExpressionPostfixTemplate(),
    new SwitchStatementPostfixTemplate(),
    new TryStatementPostfixTemplate(),
    new TryWithResourcesPostfixTemplate(),
    new StreamPostfixTemplate()
  );

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return myBuiltinTemplates;
  }

  @NotNull
  @Override
  public String getId() {
    return "builtin.java";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Java";
  }

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.' || currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull final PsiFile file, @NotNull final Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isSemicolonNeeded(file, editor)) {
      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        EditorModificationUtil.insertStringAtCaret(editor, ";", false, false);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
      }));
    }
  }

  @Override
  public void afterExpand(@NotNull final PsiFile file, @NotNull final Editor editor) {
  }

  @NotNull
  @Override
  public PsiFile preCheck(final @NotNull PsiFile copyFile, final @NotNull Editor realEditor, final int currentOffset) {
    Document document = copyFile.getViewProvider().getDocument();
    assert document != null;
    CharSequence sequence = document.getCharsSequence();
    StringBuilder fileContentWithSemicolon = new StringBuilder(sequence);
    if (isSemicolonNeeded(copyFile, realEditor)) {
      fileContentWithSemicolon.insert(currentOffset, ';');
      return PostfixLiveTemplate.copyFile(copyFile, fileContentWithSemicolon);
    }

    return copyFile;
  }

  private static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    int startOffset = CompletionInitializationContext.calcStartOffset(editor.getCaretModel().getCurrentCaret());
    return JavaCompletionContributor.semicolonNeeded(editor, file, startOffset);
  }

  @Nullable
  @Override
  public PostfixTemplateEditor createEditor(@Nullable PostfixTemplate templateToEdit) {
    if (templateToEdit == null ||
        templateToEdit instanceof JavaEditablePostfixTemplate &&
        // cannot be editable until there is no UI for editing template variables    
        !(templateToEdit instanceof ForIndexedPostfixTemplate) &&
        !(templateToEdit instanceof ArgumentPostfixTemplate) &&
        !(templateToEdit instanceof OptionalPostfixTemplate)) {
      return new JavaPostfixTemplateEditor(this, templateToEdit);
    }
    return null;
  }

  @Nullable
  @Override
  public JavaEditablePostfixTemplate readExternalTemplate(@NotNull String id, @NotNull String name, @NotNull Element template) {
    boolean useTopmostExpression = Boolean.parseBoolean(template.getAttributeValue(TOPMOST_ATTR));
    String languageLevelAttributeValue = template.getAttributeValue(LANGUAGE_LEVEL_ATTR);
    LanguageLevel languageLevel = ObjectUtils.notNull(LanguageLevel.parse(languageLevelAttributeValue), LanguageLevel.JDK_1_6);

    Set<JavaPostfixTemplateExpressionCondition> conditions = new LinkedHashSet<>();
    Element conditionsElement = template.getChild(CONDITIONS_TAG);
    if (conditionsElement != null) {
      for (Element conditionElement : conditionsElement.getChildren(CONDITION_TAG)) {
        ContainerUtil.addIfNotNull(conditions, readExternal(conditionElement));
      }
    }
    Element templateChild = template.getChild(TemplateSettings.TEMPLATE);
    if (templateChild == null) {
      return null;
    }
    TemplateImpl liveTemplate = TemplateSettings.readTemplateFromElement("", templateChild, getClass().getClassLoader());
    return new JavaEditablePostfixTemplate(id, name, liveTemplate, "", conditions, languageLevel, useTopmostExpression, this);
  }

  @Override
  public void writeExternalTemplate(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
    if (template instanceof JavaEditablePostfixTemplate) {
      parentElement.setAttribute(TOPMOST_ATTR, String.valueOf(((JavaEditablePostfixTemplate)template).isUseTopmostExpression()));

      LanguageLevel languageLevel = ((JavaEditablePostfixTemplate)template).getMinimumLanguageLevel();
      parentElement.setAttribute(LANGUAGE_LEVEL_ATTR, JpsJavaSdkType.complianceOption(languageLevel.toJavaVersion()));
      Element conditionsTag = new Element(CONDITIONS_TAG);
      for (JavaPostfixTemplateExpressionCondition condition : ((JavaEditablePostfixTemplate)template).getExpressionConditions()) {
        writeExternal(condition, conditionsTag);
      }

      Element templateTag = TemplateSettings.serializeTemplate(((EditablePostfixTemplate)template).getLiveTemplate(), null,
                                                               LazyKt.lazyOf(Collections.emptyMap()));
      parentElement.addContent(conditionsTag).addContent(templateTag);
    }
  }

  @Nullable
  private static JavaPostfixTemplateExpressionCondition readExternal(@NotNull Element condition) {
    String id = condition.getAttributeValue(ID_ATTR);
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNotPrimitiveTypeExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNotPrimitiveTypeExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition.ID.equals(id)) {
      String fqn = condition.getAttributeValue(FQN_ATTR);
      if (StringUtil.isNotEmpty(fqn)) {
        return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(fqn);
      }
    }
    return null;
  }

  private static void writeExternal(@NotNull JavaPostfixTemplateExpressionCondition condition, @NotNull Element parentElement) {
    Element element = new Element(CONDITION_TAG);
    element.setAttribute(ID_ATTR, condition.getId());
    if (condition instanceof JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition) {
      String fqn = ((JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition)condition).getFqn();
      element.setAttribute(FQN_ATTR, fqn);
    }
    parentElement.addContent(element);
  }
}
