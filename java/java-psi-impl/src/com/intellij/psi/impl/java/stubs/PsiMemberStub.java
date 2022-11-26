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
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.NamedStub;

public interface PsiMemberStub<T extends PsiMember & PsiNamedElement> extends NamedStub<T> {
  /**
   * @return whether the stubbed element is deprecated by javadoc tag
   */
  boolean isDeprecated();

  /**
   * @return whether the stubbed element might have an annotation resolving to {@link Deprecated}
   */
  default boolean hasDeprecatedAnnotation() { return true; }

  /**
   * @return whether the stubbed element might have javadoc
   */
  default boolean hasDocComment() {
    return true;
  }

}
