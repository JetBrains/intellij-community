// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Matcher extends NameUtil.Matcher {
  @Override
  boolean matches(@NotNull String name);
}
