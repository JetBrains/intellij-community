// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateEditor;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import java.util.Set;

import static com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils.readExternalConditions;
import static com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils.readExternalLiveTemplate;
import static com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils.readExternalTopmostAttribute;


public class JavaPostfixTemplateProvider extends BaseJavaPostfixTemplateProvider {
  private static final @NonNls String LANGUAGE_LEVEL_ATTR = "language-level";
  
  private final Set<PostfixTemplate> myBuiltinTemplates = ContainerUtil.newHashSet(
    new AssertStatementPostfixTemplate(this),
    new SynchronizedStatementPostfixTemplate(this),
    new ForAscendingPostfixTemplate(this),
    new ForDescendingPostfixTemplate(this),
    new WhileStatementPostfixTemplate(this),
    new SoutPostfixTemplate(this),
    new IopPostfixTemplate(this),
    new SerrPostfixTemplate(this),
    new SoufPostfixTemplate(this),
    new SoutvPostfixTemplate(this),
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
    new NewExpressionPostfixTemplate(),
    new CastVarPostfixTemplate(),
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
    new StreamPostfixTemplate(),

    new AsListToListPostfixTemplate(this),
    new ListOfToListPostfixTemplate(this),
    new NewArrayListToListPostfixTemplate(this),
    new NewHashSetToSetPostfixTemplate(this),
    new LocalDateToJavaSqlDatePostfixTemplate(this),
    new JavaUtilDateToLocalDatePostfixTemplate(this)
  );

  @Override
  public @NotNull Set<PostfixTemplate> getTemplates() {
    return myBuiltinTemplates;
  }

  @Override
  public @NotNull String getId() {
    return "builtin.java";
  }

  @Override
  public @NotNull String getPresentableName() {
    return JavaBundle.message("postfix.template.provider.name");
  }

  @Override
  public @NotNull PsiFile preCheck(@NotNull PsiFile copyFile, final @NotNull Editor realEditor, final int currentOffset) {
    Document document = copyFile.getFileDocument();
    PsiFile originalFile = copyFile.getOriginalFile();
    if (originalFile instanceof PsiCodeFragmentImpl codeFragment
        && !(copyFile instanceof PsiCodeFragment)) {
      JavaCodeFragment copyFragmentFile = null;
      if (codeFragment.getContentElementType() == JavaElementType.STATEMENTS) {
        copyFragmentFile = JavaCodeFragmentFactory.getInstance(originalFile.getProject())
          .createCodeBlockCodeFragment(document.getText(), originalFile.getContext(), copyFile.isPhysical());
      }
      else if (codeFragment.getContentElementType() == JavaElementType.EXPRESSION_TEXT &&
               codeFragment instanceof PsiExpressionCodeFragmentImpl expressionCodeFragment) {
        copyFragmentFile = JavaCodeFragmentFactory.getInstance(originalFile.getProject())
          .createExpressionCodeFragment(document.getText(), originalFile.getContext(), expressionCodeFragment.getExpectedType(),
                                        copyFile.isPhysical());
      }
      if (copyFragmentFile == null) {
        return copyFile;
      }
      copyFragmentFile.addImportsFromString(codeFragment.importsToString());
      if (copyFragmentFile instanceof PsiFileImpl fileImpl) {
        fileImpl.setOriginalFile(originalFile);
      }
      copyFile = copyFragmentFile;
    }
    return copyWithSemicolon(copyFile, realEditor, currentOffset);
  }

  @Override
  public @Nullable PostfixTemplateEditor createEditor(@Nullable PostfixTemplate templateToEdit) {
    if (templateToEdit == null ||
        templateToEdit instanceof JavaEditablePostfixTemplate &&
        // cannot be editable until there is no UI for editing template variables    
        !(templateToEdit instanceof ForIndexedPostfixTemplate) &&
        !(templateToEdit instanceof ForeachPostfixTemplate) &&
        !(templateToEdit instanceof SoutvPostfixTemplate) &&
        !(templateToEdit instanceof ArgumentPostfixTemplate) &&
        !(templateToEdit instanceof OptionalPostfixTemplate)) {
      JavaPostfixTemplateEditor editor = new JavaPostfixTemplateEditor(this);
      editor.setTemplate(templateToEdit);

      return editor;
    }
    return null;
  }

  @Override
  public @Nullable JavaEditablePostfixTemplate readExternalTemplate(@NotNull String id, @NotNull String name, @NotNull Element template) {
    TemplateImpl liveTemplate = readExternalLiveTemplate(template, this);
    if (liveTemplate == null) return null;
    Set<JavaPostfixTemplateExpressionCondition> conditions = readExternalConditions(template, JavaPostfixTemplateProvider::readExternal);
    boolean useTopmostExpression = readExternalTopmostAttribute(template);
    String languageLevelAttributeValue = template.getAttributeValue(LANGUAGE_LEVEL_ATTR);
    LanguageLevel languageLevel = ObjectUtils.notNull(LanguageLevel.parse(languageLevelAttributeValue), LanguageLevel.JDK_1_6);

    return new JavaEditablePostfixTemplate(id, name, liveTemplate, "", conditions, languageLevel, useTopmostExpression, this);
  }

  @Override
  public void writeExternalTemplate(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
    if (template instanceof JavaEditablePostfixTemplate) {

      LanguageLevel languageLevel = ((JavaEditablePostfixTemplate)template).getMinimumLanguageLevel();
      parentElement.setAttribute(LANGUAGE_LEVEL_ATTR, JpsJavaSdkType.complianceOption(languageLevel.toJavaVersion()));

      PostfixTemplatesUtils.writeExternalTemplate(template, parentElement);
    }
  }

  private static @Nullable JavaPostfixTemplateExpressionCondition readExternal(@NotNull Element condition) {
    String id = condition.getAttributeValue(PostfixTemplateExpressionCondition.ID_ATTR);
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayReferenceExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayReferenceExpressionCondition();
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
    if (JavaPostfixTemplateExpressionFqnCondition.ID.equals(id)) {
      String fqn = condition.getAttributeValue(JavaPostfixTemplateExpressionFqnCondition.FQN_ATTR);
      if (StringUtil.isNotEmpty(fqn)) {
        return new JavaPostfixTemplateExpressionFqnCondition(fqn);
      }
    }
    return null;
  }
}
