package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.util.containers.CollectionFactory.hashSet;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

/**
* @author peter
*/
public class StaticMemberProcessor {
  private final Set<PsiClass> myStaticImportedClasses = hashSet();

  public void importMembersOf(@Nullable PsiClass psiClass) {
    addIfNotNull(myStaticImportedClasses, psiClass);
  }

  public void processStaticMethods(final CompletionResultSet resultSet,
                                   final PsiElement position,
                                   final InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsert,
                                   final InsertHandler<JavaGlobalMemberLookupElement> importInsert) {
    PrefixMatcher matcher = resultSet.getPrefixMatcher();
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
        ContainerUtil.process(methods, new ReadActionProcessor<PsiMethod>() {
          @Override
          public boolean processInReadAction(PsiMethod method) {
            if (method.hasModifierProperty(PsiModifier.STATIC) && resolveHelper.isAccessible(method, position, null)) {
              final PsiClass containingClass = method.getContainingClass();
              if (containingClass != null) {
                if (!JavaCompletionUtil.isInExcludedPackage(containingClass) && !StaticImportMethodFix.isExcluded(method)) {
                  if (!hintShown[0] &&
                      FeatureUsageTracker.getInstance().isToBeShown(JavaCompletionFeatures.IMPORT_STATIC, project) &&
                      CompletionService.getCompletionService().getAdvertisementText() == null) {
                    final String shortcut = CompletionContributor.getActionShortcut("EditorRight");
                    if (shortcut != null) {
                      CompletionService.getCompletionService().setAdvertisementText("To import the method statically, press " + shortcut);
                    }
                    hintShown[0] = true;
                  }

                  final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
                  resultSet.addElement(new JavaGlobalMemberLookupElement(method, containingClass, qualifiedInsert, importInsert, shouldImport));
                }

              }
            }
            return true;
          }
        });
      }
    }
  }
}
