// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * @deprecated The old Search Everywhere is being sunset in favor of the new (Split) Search Everywhere
 * ({@code com.intellij.platform.searchEverywhere}). This functionality is obsolete.
 */
@ApiStatus.Internal
@Deprecated
public interface PromoAction {
  @Nullable
  Icon getPromotedProductIcon();
  @Nls
  String getCallToAction();
}
