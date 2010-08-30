package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaGlobalMemberNameCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.CLASS_NAME) {
      return;
    }

    final PrefixMatcher matcher = result.getPrefixMatcher();
    final String prefix = matcher.getPrefix();
    if (prefix.length() == 0 || !Character.isLowerCase(prefix.charAt(0))) {
      return;
    }

    final PsiElement position = parameters.getPosition();
    final PsiElement parent = position.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
    if (referenceExpression.isQualified()) {
      return;
    }

    completeStaticMembers(position).processStaticMethodsGlobally(result);
  }

  public static StaticMemberProcessor completeStaticMembers(final PsiElement position) {
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMethod method, @NotNull PsiClass containingClass, boolean shouldImport) {
        final JavaMethodCallElement element = new JavaMethodCallElement(method, true);
        element.setShouldBeImported(shouldImport);
        return element;
      }
    };
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiFile file = position.getContainingFile();
        if (file instanceof PsiJavaFile) {
          final PsiImportList importList = ((PsiJavaFile)file).getImportList();
          if (importList != null) {
            for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
              processor.importMembersOf(statement.resolveTargetClass());
            }
          }
        }
      }
    });

    return processor;
  }

}
