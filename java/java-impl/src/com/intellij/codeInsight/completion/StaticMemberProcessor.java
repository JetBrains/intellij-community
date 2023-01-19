// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class StaticMemberProcessor {
  private final Set<PsiClass> myStaticImportedClasses = new HashSet<>();
  private final PsiElement myPosition;
  private final Project myProject;
  private final PsiResolveHelper myResolveHelper;
  private boolean myHintShown;
  private final boolean myPackagedContext;

  protected StaticMemberProcessor(@NotNull PsiElement position) {
    myPosition = position;
    myProject = myPosition.getProject();
    myResolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    myPackagedContext = JavaCompletionUtil.inSomePackage(position);
  }

  public void importMembersOf(@NotNull PsiClass psiClass) {
    myStaticImportedClasses.add(psiClass);
  }

  public void processStaticMethodsGlobally(@NotNull PrefixMatcher matcher, @NotNull Consumer<? super LookupElement> consumer) {
    GlobalSearchScope scope = myPosition.getResolveScope();
    Collection<String> memberNames = JavaStaticMemberNameIndex.getInstance().getAllKeys(myProject);
    for (String memberName : matcher.sortMatching(memberNames)) {
      Set<PsiClass> classes = new HashSet<>();
      for (PsiMember member : JavaStaticMemberNameIndex.getInstance().getStaticMembers(memberName, myProject, scope)) {
        if (isStaticallyImportable(member)) {
          PsiClass containingClass = member.getContainingClass();
          assert containingClass != null : member.getName() + "; " + member + "; " + member.getClass();

          if (JavaCompletionUtil.isSourceLevelAccessible(myPosition, containingClass, myPackagedContext)) {
            if (member instanceof PsiMethod && !classes.add(containingClass)) continue;

            boolean shouldImport = myStaticImportedClasses.contains(containingClass);
            showHint(shouldImport);
            LookupElement item = member instanceof PsiMethod ? createItemWithOverloads((PsiMethod)member, containingClass, shouldImport) :
                                 member instanceof PsiField ? createLookupElement(member, containingClass, shouldImport) :
                                 null;
            if (item != null) {
              consumer.consume(item);
            }
          }
        }
      }
    }
  }

  @Nullable
  private LookupElement createItemWithOverloads(@NotNull PsiMethod method, @NotNull PsiClass containingClass, boolean shouldImport) {
    List<PsiMethod> overloads = ContainerUtil.findAll(containingClass.findMethodsByName(method.getName(), true),
                                                      this::isStaticallyImportable);

    assert !overloads.isEmpty();
    if (overloads.size() == 1) {
      assert method == overloads.get(0);
      return createLookupElement(method, containingClass, shouldImport);
    }

    if (overloads.get(0).getParameterList().isEmpty()) {
      overloads.add(0, overloads.remove(1));
    }
    return createLookupElement(overloads, containingClass, shouldImport);
  }

  private void showHint(boolean shouldImport) {
    if (!myHintShown && !shouldImport) {
      String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      if (StringUtil.isNotEmpty(shortcut)) {
        CompletionService.getCompletionService().setAdvertisementText(
          JavaBundle.message("to.import.a.method.statically.press.0", shortcut));
      }
      myHintShown = true;
    }
  }

  public void processMembersOfRegisteredClasses(@NotNull Condition<? super String> nameCondition, @NotNull PairConsumer<? super PsiMember, ? super PsiClass> consumer) {
    for (PsiClass psiClass : myStaticImportedClasses) {
      for (PsiMethod method : psiClass.getAllMethods()) {
        if (nameCondition.value(method.getName())) {
          if (isStaticallyImportable(method)) {
            consumer.consume(method, psiClass);
          }
        }
      }
      for (PsiField field : psiClass.getAllFields()) {
        if (nameCondition.value(field. getName())) {
          if (isStaticallyImportable(field)) {
            consumer.consume(field, psiClass);
          }
        }
      }
    }
  }

  private boolean isStaticallyImportable(@NotNull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.STATIC) && isAccessible(member) && !isExcluded(member);
  }

  private static boolean isExcluded(@NotNull PsiMember method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
  }


  public PsiElement getPosition() {
    return myPosition;
  }

  protected boolean isAccessible(@NotNull PsiMember member) {
    return myResolveHelper.isAccessible(member, myPosition, null);
  }

  @Nullable
  protected abstract LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport);

  protected abstract LookupElement createLookupElement(@NotNull List<? extends PsiMethod> overloads, @NotNull PsiClass containingClass, boolean shouldImport);
}
