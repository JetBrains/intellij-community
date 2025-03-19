// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class NonProjectScopeDisablerEP {
  public static final ExtensionPointName<NonProjectScopeDisablerEP> EP_NAME = new ExtensionPointName<>("com.intellij.goto.nonProjectScopeDisabler");

  @Attribute("disable")
  public boolean disable = true;
}
