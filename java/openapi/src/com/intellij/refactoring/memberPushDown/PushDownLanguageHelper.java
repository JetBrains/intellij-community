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
package com.intellij.refactoring.memberPushDown;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PushDownLanguageHelper<T extends MemberInfoBase<? extends PsiMember>> {
  private static final LanguageExtension<PushDownLanguageHelper> EXTENSION_POINT =
    new LanguageExtension<PushDownLanguageHelper>("com.intellij.refactoring.pushDownLanguageHelper");

  public static <T extends MemberInfoBase<? extends PsiMember>> PushDownLanguageHelper<T> forLanguage(Language language) {
    //noinspection unchecked
    return EXTENSION_POINT.forLanguage(language);
  }

  @Nullable
  public abstract PsiMember pushDownToClass(@NotNull PushDownContext context,
                                            @NotNull T memberInfo,
                                            @NotNull PsiClass targetClass,
                                            @NotNull PsiSubstitutor substitutor,
                                            @NotNull List<PsiReference> refsToRebind);

  public abstract void postprocessMember(@NotNull PushDownContext context, @NotNull PsiMember newMember, @NotNull PsiClass targetClass);
}
