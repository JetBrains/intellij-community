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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class SamePackageWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiPackage, ProximityLocation> PLACE_PACKAGE = NullableLazyKey.create("placPackage", new NullableFunction<ProximityLocation, PsiPackage>() {
    @Override
    public PsiPackage fun(ProximityLocation location) {
      return PsiTreeUtil.getContextOfType(location.getPosition(), PsiPackage.class, false);
    }
  });

  public Comparable weigh(@NotNull final PsiElement element, @Nullable final ProximityLocation location) {
    if (location == null) {
      return null;
    }
    final PsiPackage placePackage = PLACE_PACKAGE.getValue(location);
    if (placePackage == null) {
      return false;
    }

    Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    return location.getPositionModule() == elementModule &&
           placePackage.equals(PsiTreeUtil.getContextOfType(element, PsiPackage.class, false));
  }
}
