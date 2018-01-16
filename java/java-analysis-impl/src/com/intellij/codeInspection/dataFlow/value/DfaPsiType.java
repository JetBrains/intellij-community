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
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
 */
public class DfaPsiType {
  private final PsiType myPsiType;
  private final Map<Pair<DfaPsiType, DfaPsiType>, Boolean> myAssignableCache;
  private final Map<Pair<DfaPsiType, DfaPsiType>, Boolean> myConvertibleCache;
  private final int myID;

  DfaPsiType(int id, @NotNull PsiType psiType, Map<Pair<DfaPsiType, DfaPsiType>, Boolean> assignableCache, Map<Pair<DfaPsiType, DfaPsiType>, Boolean> convertibleCache) {
    myID = id;
    myPsiType = psiType;
    myAssignableCache = assignableCache;
    myConvertibleCache = convertibleCache;
  }

  @NotNull
  public PsiType getPsiType() {
    return myPsiType;
  }

  public boolean isAssignableFrom(DfaPsiType other) {
    if (other == this) return true;
    Pair<DfaPsiType, DfaPsiType> key = Pair.create(this, other);
    Boolean result = myAssignableCache.get(key);
    if (result == null) {
      myAssignableCache.put(key, result = myPsiType.isAssignableFrom(other.myPsiType));
    }
    return result;
  }

  public boolean isConvertibleFrom(DfaPsiType other) {
    if (other == this) return true;
    Pair<DfaPsiType, DfaPsiType> key = Pair.create(this, other);
    Boolean result = myConvertibleCache.get(key);
    if (result == null) {
      myConvertibleCache.put(key, result = myPsiType.isConvertibleFrom(other.myPsiType));
    }
    return result;
  }

  @Override
  public String toString() {
    return myPsiType.getPresentableText();
  }

  public int getID() {
    return myID;
  }
}
