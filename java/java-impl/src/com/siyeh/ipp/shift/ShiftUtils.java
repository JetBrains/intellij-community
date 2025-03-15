/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.shift;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;

final class ShiftUtils {

  private ShiftUtils() {}

  public static boolean isPowerOfTwo(PsiExpression rhs) {
    if (!(rhs instanceof PsiLiteralExpression literal)) {
      return false;
    }
    final Object value = literal.getValue();
    if (!(value instanceof Number)) {
      return false;
    }
    if (value instanceof Double || value instanceof Float) {
      return false;
    }
    long v = ((Number)value).longValue();
    return v > 0 && (v & (v - 1)) == 0; // https://graphics.stanford.edu/~seander/bithacks.html#DetermineIfPowerOf2
  }

  public static long getLogBase2(PsiExpression rhs) {
    final PsiLiteralExpression literal = (PsiLiteralExpression)rhs;
    final Object value = literal.getValue();
    assert value != null;
    long v = ((Number)value).longValue();
    return 63 - Long.numberOfLeadingZeros(v);
  }

  public static boolean isIntegral(PsiType lhsType) {
    return lhsType != null &&
           (lhsType.equals(PsiTypes.intType())
            || lhsType.equals(PsiTypes.shortType())
            || lhsType.equals(PsiTypes.longType())
            || lhsType.equals(PsiTypes.byteType()));
  }

  public static boolean isIntLiteral(PsiExpression rhs) {
    if (!(rhs instanceof PsiLiteralExpression literal)) {
      return false;
    }
    final Object value = literal.getValue();
    if (!(value instanceof Number)) {
      return false;
    }
    return !(value instanceof Double) && !(value instanceof Float);
  }
}
