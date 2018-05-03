/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitorEx;

/**
 * @author db
 */
public abstract class PsiExtendedTypeVisitor<X> extends PsiTypeVisitorEx<X> {
  @Override
  public X visitClassType(final PsiClassType classType) {
    super.visitClassType(classType);
    final PsiClassType.ClassResolveResult result = classType.resolveGenerics();

    if (result.getElement() != null) {
      for (final PsiType type : result.getSubstitutor().getSubstitutionMap().values()) {
        if (type != null) {
          type.accept(this);
        }
      }
    }

    return null;
  }
}
