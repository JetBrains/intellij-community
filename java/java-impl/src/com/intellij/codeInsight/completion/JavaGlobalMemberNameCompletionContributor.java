package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author peter
 */
public class JavaGlobalMemberNameCompletionContributor extends CompletionContributor {

  private static final LookupElementRenderer<LookupElement> STATIC_METHOD_RENDERER = new LookupElementRenderer<LookupElement>() {
    @Override
    public void renderElement(LookupElement element, LookupElementPresentation presentation) {
      PsiMethod method = (PsiMethod)element.getObject();
      final PsiClass containingClass = method.getContainingClass();
      presentation.setIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY)); //todo don't calculate if not a real presentation
      presentation.setItemText(method.getName());
      final String params = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                       PsiFormatUtil.SHOW_PARAMETERS,
                                                       PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
      if (containingClass != null) {
        presentation.setTailText(params + " in " + containingClass.getName());
      } else {
        presentation.setTailText(params);
      }
      final PsiType type = method.getReturnType();
      if (type != null) {
        presentation.setTypeText(type.getPresentableText());
      }
    }
  };
  private static final InsertHandler<LookupElement> STATIC_METHOD_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      PsiMethodInsertHandler.INSTANCE.handleInsert(context, item);
      final PsiClass containingClass = ((PsiMethod)item.getObject()).getContainingClass();
      if (containingClass != null) {
        PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
        final PsiReferenceExpression ref = PsiTreeUtil
          .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceExpression.class, false);
        if (ref != null) {
          ref.bindToElementViaStaticImport(containingClass);
        }
      }
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

    final Project project = position.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiShortNamesCache namesCache = JavaPsiFacade.getInstance(project).getShortNamesCache();
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    final String[] methodNames = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return namesCache.getAllMethodNames();
      }
    });
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
                    result.addElement(LookupElementDecorator.withInsertHandler(
                      LookupElementDecorator.withRenderer(LookupElementBuilder.create(method), STATIC_METHOD_RENDERER),
                      STATIC_METHOD_INSERT_HANDLER));
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
