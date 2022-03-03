// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.ProductIcons;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static java.util.Objects.requireNonNullElse;

final class ProductIconsImpl implements ProductIcons {
  private final NotNullLazyValue<Icon> myProductIcon = NotNullLazyValue.createValue(() -> {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    @SuppressWarnings("deprecation") String fallback = appInfo.getSmallIconUrl();
    return IconLoader.getIcon(requireNonNullElse(appInfo.getSmallApplicationSvgIconUrl(), fallback), ProductIconsImpl.class);
  });
  private final NotNullLazyValue<Icon> myProjectIcon = NotNullLazyValue.createValue(
    () -> PlatformUtils.isJetBrainsProduct()
          ? AllIcons.Actions.ProjectDirectory
          : myProductIcon.getValue()
  );
  private final NotNullLazyValue<Icon> myProjectNodeIcon = NotNullLazyValue.createValue(
    () -> PlatformUtils.isJetBrainsProduct()
          ? AllIcons.Nodes.IdeaProject
          : myProductIcon.getValue()
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
