// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.awt.*;

public final class BorderLayoutSerializer extends LayoutSerializer {
  public static final BorderLayoutSerializer INSTANCE = new BorderLayoutSerializer();

  private BorderLayoutSerializer() {
  }

  @Override
  void readLayout(Element element, LwContainer container) {
    final int hGap = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_HGAP, 0);
    final int vGap = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_VGAP, 0);
    container.setLayout(new BorderLayout(hGap, vGap));
  }

  @Override
  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
    component.setCustomLayoutConstraints(LwXmlReader.getRequiredString(constraintsElement,
                                                                       UIFormXmlConstants.ATTRIBUTE_BORDER_CONSTRAINT));
  }
}
