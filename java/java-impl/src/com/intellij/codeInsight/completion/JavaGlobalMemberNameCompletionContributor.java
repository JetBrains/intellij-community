package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author peter
 */
public class JavaGlobalMemberNameCompletionContributor extends CompletionContributor {

  private static final InsertHandler<JavaGlobalMemberLookupElement> STATIC_METHOD_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      PsiMethodInsertHandler.INSTANCE.handleInsert(context, item);
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

    processStaticMethods(result, position, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_METHOD_INSERT_HANDLER);
  }

  public static void processStaticMethods(final CompletionResultSet result,
                                          final PsiElement position,
                                          final InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsert, 
                                          final InsertHandler<JavaGlobalMemberLookupElement> importInsert) {
    PrefixMatcher matcher = result.getPrefixMatcher();
    final Project project = position.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiShortNamesCache namesCache = JavaPsiFacade.getInstance(project).getShortNamesCache();
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    final String[] methodNames = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return namesCache.getAllMethodNames();
      }
    });
    final boolean[] hintShown = {false};
    for (final String methodName : methodNames) {
      if (matcher.prefixMatches(methodName)) {
        final PsiMethod[] methods = ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod[]>() {
          public PsiMethod[] compute() {
            return namesCache.getMethodsByName(methodName, scope);
          }
        });
        for (final PsiMethod method : methods) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (method.hasModifierProperty(PsiModifier.STATIC) && resolveHelper.isAccessible(method, position, null)) {
                final PsiClass containingClass = method.getContainingClass();
                if (containingClass != null) {
                  if (!JavaCompletionUtil.isInExcludedPackage(containingClass) && !StaticImportMethodFix.isExcluded(method)) {
                    if (!hintShown[0] &&
                        FeatureUsageTracker.getInstance().isToBeShown(JavaCompletionFeatures.IMPORT_STATIC, project) &&
                        CompletionService.getCompletionService().getAdvertisementText() == null) {
                      final String shortcut = getActionShortcut("EditorRight");
                      if (shortcut != null) {
                        CompletionService.getCompletionService().setAdvertisementText("To import the method statically, press " + shortcut);
                      }
                      hintShown[0] = true;
                    }

                    result.addElement(new JavaGlobalMemberLookupElement(method, containingClass, qualifiedInsert, importInsert));
                  }

                }
              }
            }
          });

        }
      }
    }
  }
}
