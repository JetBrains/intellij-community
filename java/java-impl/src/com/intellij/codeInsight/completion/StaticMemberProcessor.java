package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
  private final boolean myPackagedContext;

  public StaticMemberProcessor(final PsiElement position) {
    myPosition = position;
    myProject = myPosition.getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    myPackagedContext = JavaCompletionUtil.inSomePackage(position);
  }

  public void importMembersOf(@Nullable PsiClass psiClass) {
    addIfNotNull(myStaticImportedClasses, psiClass);
  }

  public void processStaticMethodsGlobally(final CompletionResultSet resultSet) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

    final Consumer<LookupElement> consumer = new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        resultSet.addElement(element);
      }
    };

    final PrefixMatcher matcher = resultSet.getPrefixMatcher();
    final GlobalSearchScope scope = myPosition.getResolveScope();
    final PsiShortNamesCache namesCache = JavaPsiFacade.getInstance(myProject).getShortNamesCache();
    for (final String methodName : namesCache.getAllMethodNames()) {
      if (matcher.prefixMatches(methodName)) {
        Set<PsiClass> classes = new THashSet<PsiClass>();
        for (final PsiMethod method : namesCache.getMethodsByName(methodName, scope)) {
          if (isStaticallyImportable(method)) {
            final PsiClass containingClass = method.getContainingClass();
            assert containingClass != null;

            if (classes.add(containingClass) && JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
              final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
              if (!myHintShown && !shouldImport && CompletionService.getCompletionService().getAdvertisementText() == null) {
                final String shortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
                if (shortcut != null) {
                  CompletionService.getCompletionService().setAdvertisementText("To import a method statically, press " + shortcut);
                }
                myHintShown = true;
              }

              final PsiMethod[] allMethods = containingClass.getAllMethods();
              final List<PsiMethod> overloads = ContainerUtil.findAll(allMethods, new Condition<PsiMethod>() {
                @Override
                public boolean value(PsiMethod psiMethod) {
                  return methodName.equals(psiMethod.getName()) && isStaticallyImportable(psiMethod);
                }
              });

              assert !overloads.isEmpty();
              if (overloads.size() == 1) {
                assert method == overloads.get(0);
                consumer.consume(createLookupElement(method, containingClass, shouldImport));
              } else {
                if (overloads.get(0).getParameterList().getParametersCount() == 0) {
                  overloads.add(0, overloads.remove(1));
                }
                consumer.consume(createLookupElement(overloads, containingClass, shouldImport));
              }
            }
          }
        }
      }
    }
  }

  public List<PsiMember> processMembersOfRegisteredClasses(@Nullable final PrefixMatcher matcher, PairConsumer<PsiMember, PsiClass> consumer) {
    final ArrayList<PsiMember> result = CollectionFactory.arrayList();
    for (final PsiClass psiClass : myStaticImportedClasses) {
      for (final PsiMethod method : psiClass.getAllMethods()) {
        if (matcher == null || matcher.prefixMatches(method.getName())) {
          if (isStaticallyImportable(method)) {
            consumer.consume(method, psiClass);
          }
        }
      }
      for (final PsiField field : psiClass.getAllFields()) {
        if (matcher == null || matcher.prefixMatches(field.getName())) {
          if (isStaticallyImportable(field)) {
            consumer.consume(field, psiClass);
          }
        }
      }
    }
    return result;
  }


  private boolean isStaticallyImportable(final PsiMember member) {
    if (member.hasModifierProperty(PsiModifier.STATIC) && myResolveHelper.isAccessible(member, myPosition, null)) {
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass != null) {
        if (!JavaCompletionUtil.isInExcludedPackage(containingClass) &&
            (!(member instanceof PsiMethod) || !StaticImportMethodFix.isExcluded((PsiMethod)member))) {
          return true;
        }

      }
    }
    return false;
  }

  @NotNull
  protected abstract LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport);

  protected abstract LookupElement createLookupElement(@NotNull List<PsiMethod> overloads, @NotNull PsiClass containingClass, boolean shouldImport);
}
