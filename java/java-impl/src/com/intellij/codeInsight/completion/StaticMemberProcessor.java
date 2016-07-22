/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
* @author peter
*/
public abstract class StaticMemberProcessor {
  private final Set<PsiClass> myStaticImportedClasses = ContainerUtil.newHashSet();
  private final PsiElement myPosition;
  private final Project myProject;
  private final PsiResolveHelper myResolveHelper;
  private boolean myHintShown;
  private final boolean myPackagedContext;

  public StaticMemberProcessor(final PsiElement position) {
    myPosition = position;
    myProject = myPosition.getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    myPackagedContext = JavaCompletionUtil.inSomePackage(position);
  }

  public void importMembersOf(@Nullable PsiClass psiClass) {
    ContainerUtil.addIfNotNull(myStaticImportedClasses, psiClass);
  }

  public void processStaticMethodsGlobally(final PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    final GlobalSearchScope scope = myPosition.getResolveScope();
    Collection<String> memberNames = JavaStaticMemberNameIndex.getInstance().getAllKeys(myProject);
    for (final String memberName : CompletionUtil.sortMatching(matcher, memberNames)) {
      Set<PsiClass> classes = new THashSet<>();
      for (final PsiMember member : JavaStaticMemberNameIndex.getInstance().getStaticMembers(memberName, myProject, scope)) {
        if (isStaticallyImportable(member)) {
          final PsiClass containingClass = member.getContainingClass();
          assert containingClass != null : member.getName() + "; " + member + "; " + member.getClass();

          if (JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
            final boolean shouldImport = myStaticImportedClasses.contains(containingClass);
            showHint(shouldImport);
            if (member instanceof PsiMethod && classes.add(containingClass)) {
              final PsiMethod[] allMethods = containingClass.getAllMethods();
              final List<PsiMethod> overloads = ContainerUtil.findAll(allMethods, psiMethod -> memberName.equals(psiMethod.getName()) && isStaticallyImportable(psiMethod));

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
    if (!myHintShown && !shouldImport) {
      final String shortcut = CompletionContributor.getActionShortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      if (shortcut != null) {
        CompletionService.getCompletionService().setAdvertisementText("To import a method statically, press " + shortcut);
      }
      myHintShown = true;
    }
  }

  public List<PsiMember> processMembersOfRegisteredClasses(final PrefixMatcher matcher, PairConsumer<PsiMember, PsiClass> consumer) {
    final ArrayList<PsiMember> result = ContainerUtil.newArrayList();
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
