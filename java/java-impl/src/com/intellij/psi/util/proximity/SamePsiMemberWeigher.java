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
package com.intellij.psi.util.proximity;

import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class SamePsiMemberWeigher extends ProximityWeigher {
  private static final NotNullLazyKey<Boolean, ProximityLocation> INSIDE_PSI_MEMBER = NotNullLazyKey.create("insidePsiMember", new NotNullFunction<ProximityLocation, Boolean>() {
    @Override
    @NotNull
    public Boolean fun(ProximityLocation proximityLocation) {
      return PsiTreeUtil.getContextOfType(proximityLocation.getPosition(), PsiMember.class, false) != null;
    }
  });

  public Comparable weigh(@NotNull final PsiElement element, @Nullable final ProximityLocation location) {
    if (location == null) {
      return null;
    }
    if (!INSIDE_PSI_MEMBER.getValue(location)) {
      return 0;
    }

    PsiElement position = location.getPosition();
    if (!position.isPhysical() && element.isPhysical()) {
      final PsiFile file = position.getContainingFile();
      if (file != null) {
        final PsiFile originalFile = file.getOriginalFile();
        final int offset = position.getTextRange().getStartOffset();
        PsiElement candidate = originalFile.findElementAt(offset);
        if (candidate == null) {
          candidate = originalFile.findElementAt(offset - 1);
        }
        if (candidate != null) {
          position = candidate;
        }
      }
    }

    final PsiMember member = PsiTreeUtil.getContextOfType(PsiTreeUtil.findCommonContext(position, element), PsiMember.class, false);
    if (member instanceof PsiClass) return 1;
    if (member != null) return 2;
    return 0;
  }

}
