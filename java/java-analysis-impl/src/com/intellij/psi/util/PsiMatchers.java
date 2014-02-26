/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class PsiMatchers {

  private PsiMatchers() {
  }

  @NotNull
  public static PsiMatcherExpression hasName(@NotNull final String name) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (element instanceof PsiNamedElement && name.equals(((PsiNamedElement) element).getName())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasText(@NotNull final String text) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (element.getTextLength() != text.length()) return Boolean.FALSE;
        return text.equals(element.getText());
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasText(@NotNull final String... texts) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        String text = element.getText();
        return ArrayUtil.find(texts, text) != -1;
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasClass(@NotNull final Class<?> aClass) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasClass(@NotNull final Class... classes) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        for (Class<?> aClass : classes) {
          if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }
    };
  }
}
