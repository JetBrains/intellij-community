// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public abstract class JavaCodeContextType extends TemplateContextType {

  protected JavaCodeContextType(@NotNull @Nls String presentableName) {
    super(presentableName);
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    PsiFile file = templateActionContext.getFile();
    int startOffset = templateActionContext.getStartOffset();
    if (PsiUtilCore.getLanguageAtOffset(file, startOffset).isKindOf(JavaLanguage.INSTANCE)) {
      PsiElement element = file.findElementAt(startOffset);
      if (element instanceof PsiWhiteSpace) {
        return false;
      }
      return element != null && isInContext(element);
    }

    return false;
  }

  /**
   * Checks whether the element belongs to this context. Could be called inside the dumb mode!
   * 
   * @param element element to check
   * @return true if the given element belongs to this context.
   */
  protected abstract boolean isInContext(@NotNull PsiElement element);

  @Override
  public @NotNull SyntaxHighlighter createHighlighter() {
    return new JavaFileHighlighter();
  }

  @Override
  public Document createDocument(CharSequence text, Project project) {
    if (project == null) {
      return super.createDocument(text, null);
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createCodeBlockCodeFragment((String)text, psiFacade.findPackage(""), true);
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(fragment, false);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
  
  public static final class Generic extends JavaCodeContextType {
    public Generic() {
      super(JavaLanguage.INSTANCE.getDisplayName());
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return true;
    }
  }

  public static final class ConsumerFunction extends JavaCodeContextType {
    private ConsumerFunction() {
      super(JavaBundle.message("live.template.context.consumer.function"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (!(element instanceof PsiIdentifier)) return false;
      PsiReferenceExpression parent = ObjectUtils.tryCast(element.getParent(), PsiReferenceExpression.class);
      if (parent == null) return false;
      if (DumbService.isDumb(parent.getProject())) return false;
      PsiType type = ExpectedTypeUtils.findExpectedType(parent, false);
      if (type == null) return false;
      PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(type);
      return sam != null && sam.getParameterList().getParametersCount() == 1 && PsiTypes.voidType().equals(sam.getReturnType());
    }
  }

  public static final class Statement extends JavaCodeContextType {
    public Statement() {
      super(JavaBundle.message("live.template.context.statement"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isStatementContext(element);
    }

    private static boolean isStatementContext(PsiElement element) {
      if (isInJShellContext(element)) {
        return true;
      }
      if (isAfterExpression(element) || JavaStringContextType.isStringLiteral(element)) {
        return false;
      }
      PsiElement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiLambdaExpression.class);
      if (statement instanceof PsiLambdaExpression lambda) {
        PsiElement body = lambda.getBody();
        if (PsiTreeUtil.isAncestor(body, element, false)) {
          statement = body;
        }
      }

      return statement != null && statement.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
    }
  }

  private static boolean isInJShellContext(PsiElement element) {
    if (!(element.getParent() instanceof PsiReferenceExpression ref)) {
      return false;
    }
    PsiElement parent = ref.getParent();
    return parent != null && parent.getLanguage() == JShellLanguage.INSTANCE;
  }

  public static final class ElsePlace extends JavaCodeContextType {
    public ElsePlace() {
      super(JavaBundle.message("live.template.context.else"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (isAfterExpression(element) || JavaStringContextType.isStringLiteral(element)) return false;
      PsiExpressionStatement parent =
        PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, true, PsiCodeBlock.class, PsiLambdaExpression.class);
      if (parent == null) return false;
      PsiIfStatement previous = ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(parent), PsiIfStatement.class);
      if (previous == null) return false;
      PsiStatement elseBranch = previous.getElseBranch();
      while (elseBranch instanceof PsiIfStatement) {
        elseBranch = ((PsiIfStatement)elseBranch).getElseBranch();
      }
      return elseBranch == null;
    }
  }

  public static final class Expression extends JavaCodeContextType {
    public Expression() {
      super(JavaBundle.message("live.template.context.expression"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isExpressionContext(element);
    }

    private static boolean isExpressionContext(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) {
        return false;
      }
      if (((PsiJavaCodeReferenceElement)parent).isQualified()) {
        return false;
      }
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression || grandParent instanceof PsiReferenceList) {
        return false;
      }

      if (grandParent instanceof PsiTypeElement) {
        PsiElement greatGrandParent = grandParent.getParent();
        if (greatGrandParent instanceof PsiMember ||
            greatGrandParent instanceof PsiReferenceParameterList ||
            greatGrandParent instanceof PsiRecordHeader ||
            greatGrandParent instanceof PsiJavaFile) {
          return false;
        }
      }

      if (JavaKeywordCompletion.isInsideParameterList(element)) {
        return false;
      }

      return !isAfterExpression(element);
    }
  }

  private static boolean isAfterExpression(PsiElement element) {
    ProcessingContext context = new ProcessingContext();
    if (psiElement().withAncestor(1, instanceOf(PsiExpression.class))
      .afterLeaf(psiElement().withAncestor(1, psiElement(PsiExpression.class).save("prevExpr"))).accepts(element, context)) {
      PsiExpression prevExpr = (PsiExpression)context.get("prevExpr");
      if (prevExpr.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) {
        return true;
      }
    }

    return false;
  }

  public static final class Declaration extends JavaCodeContextType {
    public Declaration() {
      super(JavaBundle.message("live.template.context.declaration"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (isInJShellContext(element)) {
        return true;
      }
      if (Statement.isStatementContext(element) || Expression.isExpressionContext(element)) {
        return false;
      }

      return isInRecordHeader(element) || 
             JavaKeywordCompletion.isSuitableForClass(element) ||
             JavaKeywordCompletion.isInsideParameterList(element) ||
             PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class) != null;
    }

    private static boolean isInRecordHeader(@NotNull PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) {
        return false;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiTypeElement)) {
        return false;
      }
      PsiElement greatGrandParent = grandParent.getParent();
      return greatGrandParent instanceof PsiRecordHeader || greatGrandParent instanceof PsiRecordComponent;
    }
  }

  public static final class ImplicitClassDeclaration extends JavaCodeContextType {
    public ImplicitClassDeclaration() {
      super(JavaBundle.message("live.template.context.implicit.class.declaration"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (!PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, element)) {
        return false;
      }
      PsiFile containingFile = element.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile javaFile) || javaFile.getPackageStatement() != null) {
        return false;
      }
      //first element is identifier
      PsiElement parent = element.getParent();
      return parent instanceof PsiJavaCodeReferenceElement &&
             parent.getParent() instanceof PsiTypeElement psiTypeElement &&
             (psiTypeElement.getParent() instanceof PsiJavaFile || psiTypeElement.getParent() instanceof PsiImplicitClass);
    }
  }

  public static final class NormalClassDeclaration extends JavaCodeContextType {
    private final JavaCodeContextType declarationContext = new Declaration();
    private final JavaCodeContextType implicitClassContext = new ImplicitClassDeclaration();

    public NormalClassDeclaration() {
      super(JavaBundle.message("live.template.context.normal.class.declaration"));
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return declarationContext.isInContext(element) && !implicitClassContext.isInContext(element);
    }
  }
}
