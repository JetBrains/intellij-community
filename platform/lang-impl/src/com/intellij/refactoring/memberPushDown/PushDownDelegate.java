// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;
import static com.intellij.openapi.util.NlsContexts.DialogTitle;

public abstract class PushDownDelegate<MemberInfo extends MemberInfoBase<Member>,
                                      Member extends PsiElement> {
  public static final LanguageExtension<PushDownDelegate> EP_NAME = new LanguageExtension<>("com.intellij.refactoring.pushDown");

  protected static @Nullable <MemberInfo extends MemberInfoBase<Member>, Member extends PsiElement> PushDownDelegate<MemberInfo, Member> findDelegate(@NotNull PsiElement sourceClass) {
    return EP_NAME.forLanguage(sourceClass.getLanguage());
  }

  protected static @Nullable PushDownDelegate findDelegateForTarget(@NotNull PsiElement sourceClass, @NotNull PsiElement targetClass) {
    for (PushDownDelegate delegate : EP_NAME.allForLanguage(targetClass.getLanguage())) {
      if (delegate.isApplicableForSource(sourceClass)) {
        return delegate;
      }
    }
    return null;
  }

  /**
   * Check if delegate can process pushed members from the sourceClass.
   * It is used to find appropriate delegate to process pushing from source class to target {@link #findDelegateForTarget(PsiElement, PsiElement)}.
   *
   * Implementations are supposed to override this method when overriding default behaviour for the language,
   * e.g. pushing members from groovy class to java, groovy could provide additional delegate which inherits delegate for java and accepts groovy sources.
   * Methods to process target class should be updated to cope with source of another language (e.g. calling super on PushDownData translated to java):
   * {@link #checkTargetClassConflicts(PsiElement, PushDownData, MultiMap, NewSubClassData) },
   * {@link #pushDownToClass(PsiElement, PushDownData)}
   */
  protected abstract boolean isApplicableForSource(@NotNull PsiElement sourceClass);

  /**
   * Find classes to push members down.
   */
  protected abstract List<PsiElement> findInheritors(PushDownData<MemberInfo, Member> pushDownData);

  protected UsageInfo createUsageInfo(PsiElement element) {
    return new UsageInfo(element);
  }

  /**
   * Collect conflicts inside sourceClass assuming members would be removed,
   * e.g. check if members remaining in source class do not depend on moved members
   */
  protected abstract void checkSourceClassConflicts(PushDownData<MemberInfo, Member> pushDownData,
                                                    MultiMap<PsiElement, @DialogMessage String> conflicts);

  /**
   * Collect conflicts inside targetClass assuming methods would be pushed,
   * e.g. check if target class already has field with the same name, some references types
   * won't be accessible anymore, etc
   *
   * If {@code targetClass == null} (target class should be created), then subClassData would be not null
   */
  protected abstract void checkTargetClassConflicts(@Nullable PsiElement targetClass,
                                                    PushDownData<MemberInfo, Member> pushDownData,
                                                    MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                    @Nullable NewSubClassData subClassData);

  /**
   * Could be used e.g. to encode mutual references between moved members
   */
  protected void prepareToPush(PushDownData<MemberInfo, Member> pushDownData) {}

  /**
   * Push members to the target class adjusting visibility, comments according to the policy, etc
   */
  protected abstract void pushDownToClass(PsiElement targetClass, PushDownData<MemberInfo, Member> pushDownData);

  /**
   * Remove members from the source class according to the abstract flag.
   */
  protected abstract void removeFromSourceClass(PushDownData<MemberInfo, Member> pushDownData);

  /**
   * Called if no inheritors were found in {@link #findInheritors(PushDownData)}. Should warn that members would be deleted and
   * suggest to create new target class if applicable
   *
   * @return NewSubClassData.ABORT_REFACTORING if refactoring should be aborted
   *         null to proceed without inheritors (members would be deleted from the source class and not added to the target)
   *         new NewSubClassData(context, name) if new inheritor should be created with {@link #createSubClass(PsiElement, NewSubClassData)}
   */
  protected NewSubClassData preprocessNoInheritorsFound(PsiElement sourceClass, @DialogTitle String conflictDialogTitle) {
    final String message = RefactoringBundle.message("class.0.does.not.have.inheritors", DescriptiveNameUtil.getDescriptiveName(sourceClass)) + "\n" +
                           RefactoringBundle.message("push.down.will.delete.members");
    if (!MessageDialogBuilder.yesNo(conflictDialogTitle, message)
      .icon(Messages.getWarningIcon())
      .ask(sourceClass.getProject())) {
      return NewSubClassData.ABORT_REFACTORING;
    }
    return null;
  }

  /**
   * Create sub class with {@code subClassData.getNewClassName()} in the specified context if no inheritors were found
   */
  protected @Nullable PsiElement createSubClass(PsiElement aClass, NewSubClassData subClassData) {
    return null;
  }
}
