// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.awt.*;

public final class FlowLayoutSerializer extends LayoutSerializer {
  public static final FlowLayoutSerializer INSTANCE = new FlowLayoutSerializer();

  private FlowLayoutSerializer() {
  }

  @Override
  void readLayout(Element element, LwContainer container) {
    final int hGap = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_HGAP, 5);
    final int vGap = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_VGAP, 5);
    final int flowAlign = LwXmlReader.getOptionalInt(element, UIFormXmlConstants.ATTRIBUTE_FLOW_ALIGN, FlowLayout.CENTER);
    container.setLayout(new FlowLayout(flowAlign, hGap, vGap));
  }

  @Override
  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
  }
}
