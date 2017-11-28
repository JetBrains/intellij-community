// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Experimental
public interface RegExpCapabilitiesProvider {
  LanguageExtension<RegExpCapabilitiesProvider> EP = new LanguageExtension<>("com.intellij.regExpCapabilitiesProvider");

  @NotNull
  Set<RegExpCapability> setup(@NotNull PsiElement host, @NotNull Set<RegExpCapability> def);
}
