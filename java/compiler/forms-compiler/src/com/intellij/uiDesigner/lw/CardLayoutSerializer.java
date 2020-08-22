// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.awt.*;

/**
 * @author yole
 */
public final class CardLayoutSerializer extends LayoutSerializer {
  public static final CardLayoutSerializer INSTANCE = new CardLayoutSerializer();

  private CardLayoutSerializer() {
  }

  @Override
  void readLayout(Element element, LwContainer container) {
    final int hGap = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_HGAP, 0);
    final int vGap = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_VGAP, 0);
    container.setLayout(new CardLayout(hGap, vGap));

    String defaultCard = LwXmlReader.getOptionalString(element, UIFormXmlConstants.ATTRIBUTE_SHOW, null);
    container.putClientProperty(UIFormXmlConstants.LAYOUT_CARD, defaultCard);
  }

  @Override
  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
    final Element cardChild = LwXmlReader.getRequiredChild(constraintsElement, UIFormXmlConstants.ELEMENT_CARD);
    final String name = LwXmlReader.getRequiredString(cardChild, UIFormXmlConstants.ATTRIBUTE_NAME);
    component.setCustomLayoutConstraints(name);
  }
}
