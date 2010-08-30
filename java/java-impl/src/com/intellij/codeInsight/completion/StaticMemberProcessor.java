package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.util.containers.CollectionFactory.hashSet;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

/**
* @author peter
*/
public abstract class StaticMemberProcessor {
  private final Set<PsiClass> myStaticImportedClasses = hashSet();
  private final PsiElement myPosition;
  private final Project myProject;
  private final PsiResolveHelper myResolveHelper;
  private boolean myHintShown = false;

  public StaticMemberProcessor(final PsiElement position) {
    myPosition = position;
    myProject = myPosition.getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
  }

  public void importMembersOf(@Nullable PsiClass psiClass) {
    addIfNotNull(myStaticImportedClasses, psiClass);
  }

  public void processStaticMethodsGlobally(final CompletionResultSet resultSet) {
    final Consumer<LookupElement> consumer = new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        resultSet.addElement(element);
      }
    };

    final PrefixMatcher matcher = resultSet.getPrefixMatcher();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    final PsiShortNamesCache namesCache = JavaPsiFacade.getInstance(myProject).getShortNamesCache();
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
              processMethod(method, consumer);
            }
          });

        }
      }
    }
  }

  public void processMethodsOfRegisteredClasses(@Nullable final PrefixMatcher matcher, final Consumer<LookupElement> consumer) {
    for (final PsiClass psiClass : myStaticImportedClasses) {
      final PsiMethod[] classMethods = ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod[]>() {
        public PsiMethod[] compute() {
          return psiClass.getAllMethods();
        }
      });
      for (final PsiMethod method : classMethods) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (matcher == null || matcher.prefixMatches(method.getName())) {
              processMethod(method, consumer);
            }
          }
        });
      }
    }
  }


  private void processMethod(final PsiMethod method, final Consumer<LookupElement> consumer) {
    if (method.hasModifierProperty(PsiModifier.STATIC) && myResolveHelper.isAccessible(method, myPosition, null)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        if (!JavaCompletionUtil.isInExcludedPackage(containingClass) && !StaticImportMethodFix.isExcluded(method)) {
          final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
          if (!myHintShown &&
              !shouldImport &&
              FeatureUsageTracker.getInstance().isToBeShown(JavaCompletionFeatures.IMPORT_STATIC, myProject) &&
              CompletionService.getCompletionService().getAdvertisementText() == null) {
            final String shortcut = CompletionContributor.getActionShortcut("EditorRight");
            if (shortcut != null) {
              CompletionService.getCompletionService().setAdvertisementText("To import a method statically, press " + shortcut);
            }
            myHintShown = true;
          }

          consumer.consume(createLookupElement(method, containingClass, shouldImport));
        }

      }
    }
  }

  @NotNull
  protected abstract LookupElement createLookupElement(@NotNull PsiMethod method, @NotNull PsiClass containingClass, boolean shouldImport);
}
