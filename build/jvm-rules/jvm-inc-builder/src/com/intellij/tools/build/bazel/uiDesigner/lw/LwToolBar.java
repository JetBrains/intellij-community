// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools.build.bazel.uiDesigner.lw;

import com.intellij.tools.build.bazel.org.jdom.Element;

import java.awt.*;

public final class LwToolBar extends LwContainer {
  LwToolBar(String className) {
    super(className);
  }

  @Override
  protected LayoutManager createInitialLayout() {
    return null;
  }

  @Override
  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    readNoLayout(element, provider);
  }

  @Override
  protected void readConstraintsForChild(final Element element, final LwComponent component) {
  }
}
