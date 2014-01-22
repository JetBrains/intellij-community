/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;

/**
* Created by Max Medvedev on 10/25/13
*/
public abstract class TypePointerBase<T extends PsiType> implements SmartTypePointer {
  private Reference<T> myTypeRef;

  public TypePointerBase(@NotNull T type) {
    myTypeRef = new SoftReference<T>(type);
  }

  @Override
  public T getType() {
    T myType = SoftReference.dereference(myTypeRef);
    if (myType != null && myType.isValid()) return myType;

    myType = calcType();
    myTypeRef = myType == null ? null : new SoftReference<T>(myType);
    return myType;
  }

  @Nullable
  protected abstract T calcType();
}
