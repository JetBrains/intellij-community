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
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
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
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntroduceParameterObjectProcessor<M extends PsiNamedElement, P extends ParameterInfo, C extends IntroduceParameterObjectClassDescriptor<M, P>>
  extends FixableUsagesRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + IntroduceParameterObjectProcessor.class.getName());
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
  protected void findUsages(@NotNull List<FixableUsageInfo> usages) {
    if (myClassDescriptor.isUseExistingClass()) {
      myClassDescriptor.setExistingClassCompatibleConstructor(myClassDescriptor.findCompatibleConstructorInExistingClass(myMethod));
    }
    List<PsiNamedElement> methodHierarchy = new ArrayList<>();
    methodHierarchy.add(myMethod);
    for (UsageInfo info : ChangeSignatureProcessorBase.findUsages(myChangeInfo)) {
      if (info instanceof OverriderMethodUsageInfo) {
        methodHierarchy.add(((OverriderMethodUsageInfo)info).getOverridingMethod());
      }
      usages.add(new ChangeSignatureUsageWrapper(info));
    }

    final P[] paramsToMerge = myClassDescriptor.getParamsToMerge();
    for (PsiElement element : methodHierarchy) {
      final IntroduceParameterObjectDelegate delegate = IntroduceParameterObjectDelegate.findDelegate(element);
      if (delegate != null) {
        for (int i = 0; i < paramsToMerge.length; i++) {
          ReadWriteAccessDetector.Access access =
            delegate.collectInternalUsages(usages, (PsiNamedElement)element, myClassDescriptor, paramsToMerge[i],
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
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
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
            conflicts.putValue(element, "Method is overridden in a language that doesn't support this refactoring: " + containingFile.getName());
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

    ChangeSignatureProcessorBase
      .collectConflictsFromExtensions(new Ref<>(changeSignatureUsages.toArray(new UsageInfo[changeSignatureUsages.size()])), conflicts,
                                      myChangeInfo);

    return showConflicts(conflicts, usageInfos);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new IntroduceParameterObjectUsageViewDescriptor(myMethod);
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {
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
        .doChangeSignature(myChangeInfo, changeSignatureUsages.toArray(new UsageInfo[changeSignatureUsages.size()]));
    }
  }

  protected String getCommandName() {
    return RefactoringBundle
      .message("refactoring.introduce.parameter.object.command.name", myClassDescriptor.getClassName(), myMethod.getName());
  }

  public static class ChangeSignatureUsageWrapper extends FixableUsageInfo {
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
