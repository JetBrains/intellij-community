// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.refactoring.changeSignature.OverriderMethodUsageInfo;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public final class IntroduceParameterObjectProcessor<M extends PsiNamedElement, P extends ParameterInfo, C extends IntroduceParameterObjectClassDescriptor<M, P>>
  extends FixableUsagesRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(IntroduceParameterObjectProcessor.class);
  private final C myClassDescriptor;
  private final M myMethod;
  private final ChangeInfo myChangeInfo;
  private final P myMergedParameterInfo;
  private final IntroduceParameterObjectDelegate<M, P, C> myDelegate;
  private final ReadWriteAccessDetector.Access[] myAccessors;

  public IntroduceParameterObjectProcessor(M method,
                                           C classDescriptor,
                                           List<P> oldMethodParameters,
                                           boolean keepMethodAsDelegate) {
    super(method.getProject());
    myClassDescriptor = classDescriptor;
    myMethod = method;

    final P[] paramsToMerge = classDescriptor.getParamsToMerge();
    List<P> newParams = new ArrayList<>();
    int anchor = -1;
    for (int oldIdx = 0; oldIdx < oldMethodParameters.size(); oldIdx++) {
      P param = oldMethodParameters.get(oldIdx);
      final P mergedParameterInfo = classDescriptor.getParameterInfo(oldIdx);
      if (mergedParameterInfo != null) {
        if (anchor == -1) {
          anchor = oldIdx;
        }
      }
      else {
        newParams.add(param);
      }
    }

    myDelegate = IntroduceParameterObjectDelegate.findDelegate(method);
    LOG.assertTrue(myDelegate != null);
    myMergedParameterInfo = myDelegate.createMergedParameterInfo(classDescriptor, method, oldMethodParameters);
    newParams.add(anchor, myMergedParameterInfo);

    myChangeInfo = myDelegate.createChangeSignatureInfo(myMethod, newParams, keepMethodAsDelegate);
    myAccessors = new ReadWriteAccessDetector.Access[paramsToMerge.length];
  }

  @Override
  protected void findUsages(@NotNull List<? super FixableUsageInfo> usages) {
    if (myClassDescriptor.isUseExistingClass()) {
      myClassDescriptor.setExistingClassCompatibleConstructor(myClassDescriptor.findCompatibleConstructorInExistingClass(myMethod));
    }
    List<PsiNamedElement> methodHierarchy = new ArrayList<>();
    methodHierarchy.add(myMethod);
    for (UsageInfo info : ChangeSignatureProcessorBase.findUsages(myChangeInfo)) {
      if (info instanceof OverriderMethodUsageInfo) {
        methodHierarchy.add(((OverriderMethodUsageInfo<?>)info).getOverridingMethod());
      }
      usages.add(new ChangeSignatureUsageWrapper(info));
    }

    final P[] paramsToMerge = myClassDescriptor.getParamsToMerge();
    for (PsiNamedElement element : methodHierarchy) {
      final var delegate = IntroduceParameterObjectDelegate.findDelegate(element);
      if (delegate != null) {
        for (int i = 0; i < paramsToMerge.length; i++) {
          ReadWriteAccessDetector.Access access =
            delegate.collectInternalUsages(usages, element, myClassDescriptor, paramsToMerge[i],
                                           myMergedParameterInfo.getName());
          if (myAccessors[i] == null || access == ReadWriteAccessDetector.Access.Write) {
            myAccessors[i] = access;
          }
        }
      }
    }

    myDelegate.collectUsagesToGenerateMissedFieldAccessors(usages, myMethod, myClassDescriptor, myAccessors);
    myDelegate.collectAdditionalFixes(usages, myMethod, myClassDescriptor);


  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usageInfos = refUsages.get();
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    myDelegate.collectConflicts(conflicts, usageInfos, myMethod, myClassDescriptor);

    List<UsageInfo> changeSignatureUsages = new ArrayList<>();
    Set<PsiFile> filesWithUsages = new HashSet<>();
    for (UsageInfo usageInfo : usageInfos) {
      if (usageInfo instanceof ChangeSignatureUsageWrapper) {
        final UsageInfo info = ((ChangeSignatureUsageWrapper)usageInfo).getInfo();
        final PsiElement element = info.getElement();
        if (element != null && IntroduceParameterObjectDelegate.findDelegate(element) == null) {
          final PsiFile containingFile = element.getContainingFile();
          if (filesWithUsages.add(containingFile)) {
            String message =
              RefactoringBundle.message("dialog.message.method.overridden.in.language.that.doesn.t.support.this.refactoring",
                                        RefactoringUIUtil.getDescription(myMethod, false), element.getLanguage().getDisplayName());
            conflicts.putValue(element, StringUtil.capitalize(message));
          }
        }
        changeSignatureUsages.add(info);
      }
      else if (usageInfo instanceof FixableUsageInfo) {
        final String conflictMessage = ((FixableUsageInfo)usageInfo).getConflictMessage();
        if (conflictMessage != null) {
          conflicts.putValue(usageInfo.getElement(), conflictMessage);
        }
      }
    }

    ChangeSignatureProcessorBase.collectConflictsFromExtensions(
      new Ref<>(changeSignatureUsages.toArray(UsageInfo.EMPTY_ARRAY)), conflicts, myChangeInfo);

    return showConflicts(conflicts, usageInfos);
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new IntroduceParameterObjectUsageViewDescriptor(myMethod);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usageInfos) {
    final PsiElement aClass = myClassDescriptor.createClass(myMethod, myAccessors);
    if (aClass != null) {
      myClassDescriptor.setExistingClass(aClass);
      super.performRefactoring(usageInfos);
      List<UsageInfo> changeSignatureUsages = new ArrayList<>();
      for (UsageInfo info : usageInfos) {
        if (info instanceof ChangeSignatureUsageWrapper) {
          changeSignatureUsages.add(((ChangeSignatureUsageWrapper)info).getInfo());
        }
      }
      ChangeSignatureProcessorBase
        .doChangeSignature(myChangeInfo, changeSignatureUsages.toArray(UsageInfo.EMPTY_ARRAY));
    }
  }

  @Override
  protected @NotNull String getCommandName() {
    return RefactoringBundle
      .message("refactoring.introduce.parameter.object.command.name", myClassDescriptor.getClassName(), myMethod.getName());
  }

  public static final class ChangeSignatureUsageWrapper extends FixableUsageInfo {
    private final UsageInfo myInfo;

    public ChangeSignatureUsageWrapper(UsageInfo info) {
      super(info.getElement());
      myInfo = info;
    }

    public UsageInfo getInfo() {
      return myInfo;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
    }
  }
}
