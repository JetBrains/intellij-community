/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class IntroduceParameterUtil {
  private IntroduceParameterUtil() {
  }


  public static boolean insideMethodToBeReplaced(PsiElement methodUsage, final PsiMethod methodToReplaceIn) {
    PsiElement parent = methodUsage.getParent();
    while (parent != null) {
      if (parent.equals(methodToReplaceIn)) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public static boolean isMethodUsage(UsageInfo usageInfo) {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (processor.isMethodUsage(usageInfo)) return true;
    }
    return false;
  }

  public static void addSuperCall(UsageInfo usage, UsageInfo[] usages, final IntroduceParameterData data)
    throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processAddSuperCall(data, usage, usages)) break;
    }
  }

  public static void addDefaultConstructor(UsageInfo usage, UsageInfo[] usages, final IntroduceParameterData data)
    throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processAddDefaultConstructor(data, usage, usages)) break;
    }
  }

  public static void changeExternalUsage(UsageInfo usage, UsageInfo[] usages, final IntroduceParameterData data)
    throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processChangeMethodUsage(data, usage, usages)) break;
    }
  }

  public static void changeMethodSignatureAndResolveFieldConflicts(UsageInfo usage,
                                                                   UsageInfo[] usages,
                                                                   final IntroduceParameterData data)
    throws IncorrectOperationException {
    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      if (!processor.processChangeMethodSignature(data, usage, usages)) break;
    }
  }

  public static void processUsages(UsageInfo[] usages, IntroduceParameterData data) {
    PsiManager manager = PsiManager.getInstance(data.getProject());

    List<UsageInfo> methodUsages = new ArrayList<>();

    for (UsageInfo usage : usages) {
      if (usage instanceof InternalUsageInfo) continue;

      if (usage instanceof DefaultConstructorImplicitUsageInfo) {
        addSuperCall(usage, usages, data);
      }
      else if (usage instanceof NoConstructorClassUsageInfo) {
        addDefaultConstructor(usage, usages, data);
      }
      else {
        PsiElement element = usage.getElement();
        if (element instanceof PsiMethod) {
          if (!manager.areElementsEquivalent(element, data.getMethodToReplaceIn())) {
            methodUsages.add(usage);
          }
        }
        else if (!data.isGenerateDelegate()) {
          changeExternalUsage(usage, usages, data);
        }
      }
    }

    for (UsageInfo usage : methodUsages) {
      changeMethodSignatureAndResolveFieldConflicts(usage, usages, data);
    }
  }

  public static boolean isMethodInUsages(IntroduceParameterData data, PsiMethod method, UsageInfo[] usages) {
    PsiManager manager = PsiManager.getInstance(data.getProject());
    for (UsageInfo info : usages) {
      if (!(info instanceof DefaultConstructorImplicitUsageInfo) &&  manager.areElementsEquivalent(info.getElement(), method)) {
        return true;
      }
    }
    return false;
  }
}
