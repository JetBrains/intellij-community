// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

abstract class LayoutSerializer {
  abstract void readLayout(Element element, LwContainer container);

  abstract void readChildConstraints(final Element constraintsElement, final LwComponent component);
}
