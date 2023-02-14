// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public final class LwHSpacer extends LwAtomicComponent {
  LwHSpacer() {
    super("com.intellij.uiDesigner.core.Spacer");
  }

  @Override
  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    readBase(element);
    readConstraints(element);
  }
}
