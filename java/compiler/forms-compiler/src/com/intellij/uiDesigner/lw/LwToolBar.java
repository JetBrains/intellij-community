// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

import org.jdom.Element;

import java.awt.*;

public class LwToolBar extends LwContainer {
  public LwToolBar(String className) {
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
