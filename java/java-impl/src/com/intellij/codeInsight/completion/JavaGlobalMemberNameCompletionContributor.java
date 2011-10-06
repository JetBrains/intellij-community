package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class JavaGlobalMemberNameCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.CLASS_NAME) {
      return;
    }

    if (result.getPrefixMatcher().getPrefix().length() == 0) {
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

    completeStaticMembers(parameters).processStaticMethodsGlobally(result);
  }

  public static StaticMemberProcessor completeStaticMembers(CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    final PsiElement originalPosition = parameters.getOriginalPosition();
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull final PsiClass containingClass, boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);
        
        if (member instanceof PsiMethod) {
          return new JavaMethodCallElement((PsiMethod)member, shouldImport, false);
        }
        return new VariableLookupItem((PsiField)member, shouldImport);
      }

      @Override
      protected LookupElement createLookupElement(@NotNull List<PsiMethod> overloads,
                                                  @NotNull PsiClass containingClass,
                                                  boolean shouldImport) {
        shouldImport |= originalPosition != null && PsiTreeUtil.isAncestor(containingClass, originalPosition, false);

        final JavaMethodCallElement element = new JavaMethodCallElement(overloads.get(0), shouldImport, true);
        element.putUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE, overloads);
        return element;
      }
    };
    final PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          processor.importMembersOf(statement.resolveTargetClass());
        }
      }
    }

    return processor;
  }
}
