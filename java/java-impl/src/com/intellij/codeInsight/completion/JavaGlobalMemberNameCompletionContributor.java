package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
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
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull final PsiClass containingClass, boolean shouldImport) {
        if (member instanceof PsiMethod) {
          final JavaMethodCallElement element = new JavaMethodCallElement((PsiMethod)member, true);
          element.setShouldBeImported(shouldImport);
          return element;
        }
        return new VariableLookupItem((PsiVariable)member) {
          @Override
          public void handleInsert(InsertionContext context) {
            context.commitDocument();
            final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceExpression.class, false);
            if (ref != null) {
              ref.bindToElementViaStaticImport(containingClass);
            }
            super.handleInsert(context);
          }
        };
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
