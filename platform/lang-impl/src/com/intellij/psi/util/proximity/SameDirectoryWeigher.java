/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;

/**
 * NOTE: This class is only registered in platform-based IDEs. In IDEA, SamePackageWeigher is used instead.
 *
 * @author yole
 */
public class SameDirectoryWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiDirectory, ProximityLocation>
    PLACE_DIRECTORY = NullableLazyKey.create("placeDirectory", new NullableFunction<ProximityLocation, PsiDirectory>() {
    @Override
    public PsiDirectory fun(ProximityLocation location) {
      return PsiTreeUtil.getParentOfType(location.getPosition(), PsiDirectory.class, false);
    }
  });

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    final PsiDirectory placeDirectory = PLACE_DIRECTORY.getValue(location);
    if (placeDirectory == null) {
      return false;
    }

    return placeDirectory.equals(PsiTreeUtil.getParentOfType(element, PsiDirectory.class, false));
  }
}
