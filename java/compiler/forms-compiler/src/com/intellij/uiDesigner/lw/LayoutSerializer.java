// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class LayoutSerializer {
  abstract void readLayout(Element element, LwContainer container);

  abstract void readChildConstraints(final Element constraintsElement, final LwComponent component);
}
