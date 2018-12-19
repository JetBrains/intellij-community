// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.ProductIcons;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProductsIconsImpl implements ProductIcons {
  private final NotNullLazyValue<Icon> myProductIcon = NotNullLazyValue.createValue(
    () -> IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl())
  );
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

  @NotNull
  @Override
  public Icon getProjectNodeIcon() {
    return myProjectNodeIcon.getValue();
  }

  @NotNull
  @Override
  public Icon getProjectIcon() {
    return myProjectIcon.getValue();
  }

  @NotNull
  @Override
  public Icon getProductIcon() {
    return myProductIcon.getValue();
  }
}
