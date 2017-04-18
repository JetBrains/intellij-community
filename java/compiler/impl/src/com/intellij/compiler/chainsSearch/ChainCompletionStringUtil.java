/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class ChainCompletionStringUtil {
  private ChainCompletionStringUtil() {}

  /**
   * CAUTION: isPrimitiveOrArrayOfPrimitives("java.lang.String") == true,
   *          isPrimitiveOrArrayOfPrimitives("java.lang.Object") == true
   *          isPrimitiveOrArrayOfPrimitives("java.lang.Class") == true
   */
  public static boolean isPrimitiveOrArrayOfPrimitives(@NotNull PsiType type) {
    type = type.getDeepComponentType();
    if (type instanceof PsiPrimitiveType) return true;
    if (!(type instanceof PsiClassType)) return false;
    if (PRIMITIVES_SHORT_NAMES.contains(((PsiClassType)type).getClassName())) return false;
    final PsiClass resolvedClass = ((PsiClassType)type).resolve();
    if (resolvedClass == null) return false;
    final String qName = resolvedClass.getQualifiedName();
    if (qName == null) return false;
    for (String name : PRIMITIVES_NAMES) {
      if (name.equals(qName)) {
        return true;
      }
    }
    return false;
  }

  private static final String[] PRIMITIVES_NAMES = new String [] {CommonClassNames.JAVA_LANG_STRING,
                                                                        CommonClassNames.JAVA_LANG_OBJECT,
                                                                        CommonClassNames.JAVA_LANG_CLASS};
  private static final Set<String> PRIMITIVES_SHORT_NAMES = ContainerUtil.set("String", "Object", "Class");
}
