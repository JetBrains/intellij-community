/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public abstract class JavaCodeContextType extends TemplateContextType {

  protected JavaCodeContextType(@NotNull @NonNls String id,
                                @NotNull @Nls String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    super(id, presentableName, baseContextType);
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
   * @return true if given element belongs to this context.
   */
  protected abstract boolean isInContext(@NotNull PsiElement element);

  @NotNull
  @Override
  public SyntaxHighlighter createHighlighter() {
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
  
  public static class Generic extends JavaCodeContextType {
    public Generic() {
      super("JAVA_CODE", JavaLanguage.INSTANCE.getDisplayName(), EverywhereContextType.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return true;
    }
  }

  public static class ConsumerFunction extends JavaCodeContextType {
    protected ConsumerFunction() {
      super("JAVA_CONSUMER", JavaBundle.message("live.template.context.consumer.function"), Generic.class);
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
      return sam != null && sam.getParameterList().getParametersCount() == 1 && PsiType.VOID.equals(sam.getReturnType());
    }
  }

  public static class Statement extends JavaCodeContextType {
    public Statement() {
      super("JAVA_STATEMENT", JavaBundle.message("live.template.context.statement"), Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isStatementContext(element);
    }

    private static boolean isStatementContext(PsiElement element) {
      if (isAfterExpression(element) || JavaStringContextType.isStringLiteral(element)) {
        return false;
      }
      
      PsiElement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiLambdaExpression.class);
      if (statement instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)statement).getBody();
        if (PsiTreeUtil.isAncestor(body, element, false)) {
          statement = body;
        }
      }

      return statement != null && statement.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
    }
  }

  public static class ElsePlace extends JavaCodeContextType {
    public ElsePlace() {
      super("JAVA_ELSE_PLACE", JavaBundle.message("live.template.context.else"), Generic.class);
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

  public static class Expression extends JavaCodeContextType {
    public Expression() {
      super("JAVA_EXPRESSION", JavaBundle.message("live.template.context.expression"), Generic.class);
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
      PsiElement grandpa = parent.getParent();
      if (grandpa instanceof PsiMethodCallExpression || grandpa instanceof PsiReferenceList) {
        return false;
      }

      if (grandpa instanceof PsiTypeElement && (grandpa.getParent() instanceof PsiMember || grandpa.getParent() instanceof PsiReferenceParameterList)) {
        return false;
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

  public static class Declaration extends JavaCodeContextType {
    public Declaration() {
      super("JAVA_DECLARATION", JavaBundle.message("live.template.context.declaration"), Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (Statement.isStatementContext(element) || Expression.isExpressionContext(element)) {
        return false;
      }

      return JavaKeywordCompletion.isSuitableForClass(element) ||
             JavaKeywordCompletion.isInsideParameterList(element) ||
             PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class) != null;
    }
  }


}
