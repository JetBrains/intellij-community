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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class IntroduceParameterObjectDelegate<M extends PsiNamedElement, P extends ParameterInfo, C extends IntroduceParameterObjectClassDescriptor<M, P>> {
  public enum Accessor {
    Getter, Setter;

  }
  private static final LanguageExtension<IntroduceParameterObjectDelegate> EP_NAME =
    new LanguageExtension<IntroduceParameterObjectDelegate>("com.intellij.refactoring.introduceParameterObject");

  public static <M extends PsiNamedElement, P extends ParameterInfo, C extends IntroduceParameterObjectClassDescriptor<M, P>> IntroduceParameterObjectDelegate<M, P, C> findDelegate(
    @NotNull PsiElement element) {
    return findDelegate(element.getLanguage());
  }

  public static <M extends PsiNamedElement, P extends ParameterInfo, C extends IntroduceParameterObjectClassDescriptor<M, P>> IntroduceParameterObjectDelegate<M, P, C> findDelegate(
    Language language) {
    return EP_NAME.forLanguage(language);
  }

  public abstract P createMergedParameterInfo(Project project,
                                              C descriptor,
                                              int[] paramsToMerge,
                                              M method);

  public abstract ChangeInfo createChangeSignatureInfo(M method,
                                                       List<P> infos, boolean delegate);

  public abstract <M1 extends PsiNamedElement, P1 extends ParameterInfo>
  Accessor collectInternalUsages(Collection<FixableUsageInfo> usages,
                                 M overridingMethod,
                                 M1 element,
                                 IntroduceParameterObjectClassDescriptor<M1, P1> classDescriptor, int parameterIdx,
                                 String mergedParamName);

  public abstract void collectAccessibilityUsages(Collection<FixableUsageInfo> usages,
                                                  M method, C descriptor,
                                                  Accessor[] accessors);

  public abstract void collectConflicts(MultiMap<PsiElement, String> conflicts,
                                        UsageInfo[] infos, M method, C classDescriptor);
}
