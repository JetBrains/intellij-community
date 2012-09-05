/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The whole arrangement idea is to allow to change file entries order according to the user-provided rules.
 * <p/>
 * That means that we can re-use the same mechanism during, say, new members generation - arrangement rules can be used to
 * determine position where a new element should be inserted.
 * <p/>
 * This service provides utility methods for that.
 * 
 * @author Denis Zhdanov
 * @since 9/4/12 11:12 AM
 */
public class MemberOrderService {
  
  public static final int UNDEFINED_WEIGHT = -1;

  /**
   * Allows to get a given member's weight according to the
   * {@link CommonCodeStyleSettings#getArrangementRules() user-defined arrangement rules}.
   * <p/>
   * That means that we can call this method for different members and derive their relative order by comparing their weights.
   * 
   * @param member    target member which weight should be calculated
   * @param settings  code style settings to use
   * @param context   given member's context (if any). Is useful when we have, say, an existing class and want to know where
   *                  to insert a new field. Not only 'by type' filtering might be exploited (like 'fields before methods') but
   *                  'by name' as well, i.e. we want to insert a new field to the 'fidles' group and define its position
   *                  according to the lexicographical fields order
   * @return          given member's weight if the one can be computed;
   *                  {@link #UNDEFINED_WEIGHT} otherwise
   */
  public int getMemberOrderWeight(@NotNull PsiElement member, @NotNull CommonCodeStyleSettings settings, @Nullable PsiElement context) {
    // TODO den implement
    return UNDEFINED_WEIGHT;
  }
}
