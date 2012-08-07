package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
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

  public void processStaticMethodsGlobally(final PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    final GlobalSearchScope scope = myPosition.getResolveScope();
    Collection<String> memberNames = JavaStaticMemberNameIndex.getInstance().getAllKeys(myProject);
    for (final String memberName : CompletionUtil.sortMatching(matcher, memberNames)) {
      Set<PsiClass> classes = new THashSet<PsiClass>();
      for (final PsiMember member : JavaStaticMemberNameIndex.getInstance().getStaticMembers(memberName, myProject, scope)) {
        if (isStaticallyImportable(member)) {
          final PsiClass containingClass = member.getContainingClass();
          assert containingClass != null : member.getName() + "; " + member + "; " + member.getClass();

          if (JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
            final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
            showHint(shouldImport);
            if (member instanceof PsiMethod && classes.add(containingClass)) {
              final PsiMethod[] allMethods = containingClass.getAllMethods();
              final List<PsiMethod> overloads = ContainerUtil.findAll(allMethods, new Condition<PsiMethod>() {
                @Override
                public boolean value(PsiMethod psiMethod) {
                  return memberName.equals(psiMethod.getName()) && isStaticallyImportable(psiMethod);
                }
              });

              assert !overloads.isEmpty();
              if (overloads.size() == 1) {
                assert member == overloads.get(0);
                consumer.consume(createLookupElement(member, containingClass, shouldImport));
              } else {
                if (overloads.get(0).getParameterList().getParametersCount() == 0) {
                  overloads.add(0, overloads.remove(1));
                }
                consumer.consume(createLookupElement(overloads, containingClass, shouldImport));
              }
            } else if (member instanceof PsiField) {
              consumer.consume(createLookupElement(member, containingClass, shouldImport));
            }
          }
        }
      }
    }
  }

  private void showHint(boolean shouldImport) {
    if (!myHintShown && !shouldImport && CompletionService.getCompletionService().getAdvertisementText() == null) {
      final String shortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      if (shortcut != null) {
        CompletionService.getCompletionService().setAdvertisementText("To import a method statically, press " + shortcut);
      }
      myHintShown = true;
    }
  }

  public List<PsiMember> processMembersOfRegisteredClasses(final PrefixMatcher matcher, PairConsumer<PsiMember, PsiClass> consumer) {
    final ArrayList<PsiMember> result = CollectionFactory.arrayList();
    for (final PsiClass psiClass : myStaticImportedClasses) {
      for (final PsiMethod method : psiClass.getAllMethods()) {
        if (matcher.prefixMatches(method.getName())) {
          if (isStaticallyImportable(method)) {
            consumer.consume(method, psiClass);
          }
        }
      }
      for (final PsiField field : psiClass.getAllFields()) {
        if (matcher.prefixMatches(field. getName())) {
          if (isStaticallyImportable(field)) {
            consumer.consume(field, psiClass);
          }
        }
      }
    }
    return result;
  }


  private boolean isStaticallyImportable(final PsiMember member) {
    return member.hasModifierProperty(PsiModifier.STATIC) && isAccessible(member) && !StaticImportMethodFix.isExcluded(member);
  }

  protected boolean isAccessible(PsiMember member) {
    return myResolveHelper.isAccessible(member, myPosition, null);
  }

  @NotNull
  protected abstract LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport);

  protected abstract LookupElement createLookupElement(@NotNull List<PsiMethod> overloads, @NotNull PsiClass containingClass, boolean shouldImport);
}
