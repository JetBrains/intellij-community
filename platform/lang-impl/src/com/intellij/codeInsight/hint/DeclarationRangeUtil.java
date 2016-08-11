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

package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.MixinExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DeclarationRangeUtil {
  public static Map<Class,DeclarationRangeHandler> ourDeclarationRangeRegistry = new HashMap<>();

  private DeclarationRangeUtil() {
  }

  public static void setDeclarationHandler(@NotNull Class clazz, DeclarationRangeHandler handler) {
    ourDeclarationRangeRegistry.put(clazz, handler);
  }

  public static @NotNull TextRange getDeclarationRange(PsiElement container) {
    final TextRange textRange = getPossibleDeclarationAtRange(container);
    assert textRange != null :"Declaration range is invalid for "+container.getClass();
    return textRange;
  }

  public static @Nullable
  TextRange getPossibleDeclarationAtRange(final PsiElement container) {
    DeclarationRangeHandler handler = MixinExtension.getInstance(DeclarationRangeHandler.EP_NAME, container);
    if (handler != null) {
      return handler.getDeclarationRange(container);
    }
    else {
      for(Class clazz:ourDeclarationRangeRegistry.keySet()) {
        if (clazz.isInstance(container)) {
          final DeclarationRangeHandler handler2 = ourDeclarationRangeRegistry.get(clazz);
          if (handler2 != null) return handler2.getDeclarationRange(container);
        }
      }

      return null;
    }
  }

}