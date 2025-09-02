// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.GridBagConverter;
import org.jdom.Element;

import java.awt.*;

final class GridBagLayoutSerializer extends GridLayoutSerializer {
  private GridBagLayoutSerializer() {
  }

  public static final GridBagLayoutSerializer INSTANCE = new GridBagLayoutSerializer();

  @Override
  void readLayout(Element element, LwContainer container) {
    container.setLayout(new GridBagLayout());
  }

  @Override
  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
    super.readChildConstraints(constraintsElement, component);
    GridBagConstraints gbc = new GridBagConstraints();
    GridBagConverter.constraintsToGridBag(component.getConstraints(), gbc);
    final Element gridBagElement = LwXmlReader.getChild(constraintsElement, UIFormXmlConstants.ELEMENT_GRIDBAG);
    if (gridBagElement != null) {
      if (gridBagElement.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_TOP) != null) {
        gbc.insets = LwXmlReader.readInsets(gridBagElement);
      }
      gbc.weightx = LwXmlReader.getOptionalDouble(gridBagElement, UIFormXmlConstants.ATTRIBUTE_WEIGHTX, 0.0);
      gbc.weighty = LwXmlReader.getOptionalDouble(gridBagElement, UIFormXmlConstants.ATTRIBUTE_WEIGHTY, 0.0);
      gbc.ipadx = LwXmlReader.getOptionalInt(gridBagElement, UIFormXmlConstants.ATTRIBUTE_IPADX, 0);
      gbc.ipady = LwXmlReader.getOptionalInt(gridBagElement, UIFormXmlConstants.ATTRIBUTE_IPADY, 0);
    }
    component.setCustomLayoutConstraints(gbc);
  }
}
