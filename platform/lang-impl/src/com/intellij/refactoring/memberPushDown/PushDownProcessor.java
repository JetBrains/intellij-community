// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;
import static com.intellij.openapi.util.NlsContexts.DialogTitle;

public class PushDownProcessor<MemberInfo extends MemberInfoBase<Member>,
                               Member extends PsiElement,
                               Klass extends PsiElement> extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(PushDownProcessor.class);

  private NewSubClassData mySubClassData;
  private final PushDownDelegate<MemberInfo, Member> myDelegate;
  private final PushDownData<MemberInfo, Member> myPushDownData;

  public PushDownProcessor(@NotNull Klass sourceClass,
                           @NotNull List<MemberInfo> memberInfos,
                           @NotNull DocCommentPolicy javaDocPolicy) {
    this(sourceClass, memberInfos, javaDocPolicy, false);
  }

  @ApiStatus.Experimental
  public PushDownProcessor(@NotNull Klass sourceClass,
                           @NotNull List<MemberInfo> memberInfos,
                           @NotNull DocCommentPolicy javaDocPolicy,
                           boolean preserveExternalLinks) {
    super(sourceClass.getProject());
    myDelegate = PushDownDelegate.findDelegate(sourceClass);
    LOG.assertTrue(myDelegate != null);
    myPushDownData = new PushDownData<>(sourceClass, memberInfos, javaDocPolicy, preserveExternalLinks);
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new PushDownUsageViewDescriptor<>((Klass)myPushDownData.getSourceClass(), myPushDownData.getMembersToMove());
  }

  @Override
  protected @NotNull Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Collections.singletonList(myPushDownData.getSourceClass());
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myPushDownData.getSourceClass());
    data.addElements(ContainerUtil.map(myPushDownData.getMembersToMove(), MemberInfoBase::getMember));
    return data;
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    final List<PsiElement> elements = new ArrayList<>();
    for (UsageInfo usage : usages) {
      elements.add(usage.getElement());
    }
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(elements);
    return data;
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final List<PsiElement> inheritors = myDelegate.findInheritors(myPushDownData);
    return ContainerUtil.map2Array(inheritors, UsageInfo.EMPTY_ARRAY, myDelegate::createUsageInfo);
  }

  @Override
  protected boolean preprocessUsages(final @NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    myDelegate.checkSourceClassConflicts(myPushDownData, conflicts);
    final UsageInfo[] usagesIn = refUsages.get();
    if (usagesIn.length == 0) {
      mySubClassData = myDelegate.preprocessNoInheritorsFound(myPushDownData.getSourceClass(), getCommandName());
      if (mySubClassData == NewSubClassData.ABORT_REFACTORING) {
        return false;
      }
    }
    Runnable runnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
      if (mySubClassData != null) {
        myDelegate.checkTargetClassConflicts(null, myPushDownData, conflicts, mySubClassData);
      }
      else {
        for (UsageInfo usage : usagesIn) {
          final PsiElement element = usage.getElement();
          if (element != null) {
            final PushDownDelegate delegate = PushDownDelegate.findDelegateForTarget(myPushDownData.getSourceClass(), element);
            if (delegate != null) {
              delegate.checkTargetClassConflicts(element, myPushDownData, conflicts, null);
            }
            else {
              conflicts.putValue(element, RefactoringBundle.message("dialog.message.not.supported.source.target.pair.detected"));
            }
          }
        }
      }
    });

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }

    return showConflicts(conflicts, usagesIn);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    if(elements.length == 1) {
      myPushDownData.setSourceClass(elements[0]);
    }
    else {
      LOG.assertTrue(false);
    }
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    try {
      pushDownToClasses(usages);
      myDelegate.removeFromSourceClass(myPushDownData);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void pushDownToClasses(UsageInfo @NotNull [] usages) {
    myDelegate.prepareToPush(myPushDownData);
    final PsiElement sourceClass = myPushDownData.getSourceClass();
    if (mySubClassData != null) {
      final PsiElement subClass = myDelegate.createSubClass(sourceClass, mySubClassData);
      if (subClass != null) {
        myDelegate.pushDownToClass(subClass, myPushDownData);
      }
    }
    else {
      for (UsageInfo usage : usages) {
        final PsiElement element = usage.getElement();
        if (element != null) {
          final PushDownDelegate targetDelegate = PushDownDelegate.findDelegateForTarget(sourceClass, element);
          if (targetDelegate != null) {
            targetDelegate.pushDownToClass(element, myPushDownData);
          }
        }
      }
    }
  }

  @Override
  protected @NotNull @DialogTitle String getCommandName() {
    return RefactoringBundle.message("push.members.down.title");
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.push.down";
  }
}
