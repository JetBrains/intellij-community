// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.ProductIcons;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.NotNullLazyValue.lazy;

final class ProductIconsImpl implements ProductIcons {
  private final NotNullLazyValue<Icon> myProductIcon = lazy(
    () -> IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallApplicationSvgIconUrl(), ProductIconsImpl.class.getClassLoader())
  );
  private final NotNullLazyValue<Icon> myProjectIcon = lazy(
    () -> PlatformUtils.isJetBrainsProduct() ? AllIcons.Actions.ProjectDirectory : myProductIcon.getValue()
  );
  private final NotNullLazyValue<Icon> myProjectNodeIcon = lazy(
    () -> PlatformUtils.isJetBrainsProduct() ? AllIcons.Nodes.IdeaProject : myProductIcon.getValue()
  );

  @Override
  public @NotNull Icon getProjectNodeIcon() {
    return myProjectNodeIcon.getValue();
  }

  @Override
  public @NotNull Icon getProjectIcon() {
    return myProjectIcon.getValue();
  }

  @Override
  public @NotNull Icon getProductIcon() {
    return myProductIcon.getValue();
  }
}
