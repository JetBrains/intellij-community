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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocCommentOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PreferAccessibleWeigher extends CompletionWeigher {

  public MyEnum weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    final Object object = item.getObject();
    if (object instanceof PsiDocCommentOwner) {
      final PsiDocCommentOwner member = (PsiDocCommentOwner)object;
      if (!member.isValid()) {
        return MyEnum.NORMAL;
      }

      if (!JavaPsiFacade.getInstance(member.getProject()).getResolveHelper().isAccessible(member, location.getCompletionParameters().getPosition(), null)) return MyEnum.INACCESSIBLE;
      if (member.isDeprecated()) return MyEnum.DEPRECATED;
    }
    return MyEnum.NORMAL;
  }

  private enum MyEnum {
    INACCESSIBLE,
    DEPRECATED,
    NORMAL
  }
}
