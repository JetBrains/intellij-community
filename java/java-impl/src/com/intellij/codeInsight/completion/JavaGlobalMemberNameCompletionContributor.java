package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaGlobalMemberNameCompletionContributor extends CompletionContributor {

  private static final InsertHandler<JavaGlobalMemberLookupElement> STATIC_METHOD_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      PsiMethodInsertHandler.INSTANCE.handleInsert(context, item);
      if (item.getObject().getReturnType() == PsiType.VOID) {
        TailType.SEMICOLON.processTail(context.getEditor(), context.getTailOffset());
      }
      final PsiClass containingClass = item.getContainingClass();
      PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
      final PsiReferenceExpression ref = PsiTreeUtil
        .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceExpression.class, false);
      if (ref != null) {
        ref.bindToElementViaStaticImport(containingClass);
      }
    }
  };
  private static final InsertHandler<JavaGlobalMemberLookupElement> QUALIFIED_METHOD_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      PsiMethodInsertHandler.INSTANCE.handleInsert(context, item);
      if (item.getObject().getReturnType() == PsiType.VOID) {
        TailType.SEMICOLON.processTail(context.getEditor(), context.getTailOffset());
      }
      context.getDocument().insertString(context.getStartOffset(), ".");
      JavaCompletionUtil.insertClassReference(item.getContainingClass(), context.getFile(), context.getStartOffset());
    }
  };

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
    final StaticMemberProcessor processor = new StaticMemberProcessor(position, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_METHOD_INSERT_HANDLER) {
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
