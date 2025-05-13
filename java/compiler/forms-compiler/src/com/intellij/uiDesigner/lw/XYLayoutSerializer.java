// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jdom.Element;

import java.awt.*;

final class XYLayoutSerializer extends LayoutSerializer {
  static XYLayoutSerializer INSTANCE = new XYLayoutSerializer();

  private XYLayoutSerializer() {
  }

  @Override
  void readLayout(Element element, LwContainer container) {
    container.setLayout(new XYLayoutManager());
  }

  @Override
  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
    final Element xyElement = LwXmlReader.getChild(constraintsElement, "xy");
    if(xyElement != null){
      component.setBounds(
        new Rectangle(
          LwXmlReader.getRequiredInt(xyElement, "x"),
          LwXmlReader.getRequiredInt(xyElement, "y"),
          LwXmlReader.getRequiredInt(xyElement, "width"),
          LwXmlReader.getRequiredInt(xyElement, "height")
        )
      );
    }
  }
}
