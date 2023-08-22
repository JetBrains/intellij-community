// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.Utils;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;
import org.jdom.Element;

import java.util.List;

public final class FormLayoutSerializer extends GridLayoutSerializer {
  private FormLayoutSerializer() {
  }

  public static final FormLayoutSerializer INSTANCE = new FormLayoutSerializer();

  public static final CellConstraints.Alignment[] ourHorizontalAlignments = {
    CellConstraints.LEFT, CellConstraints.CENTER, CellConstraints.RIGHT, CellConstraints.FILL
  };
  public static final CellConstraints.Alignment[] ourVerticalAlignments = {
    CellConstraints.TOP, CellConstraints.CENTER, CellConstraints.BOTTOM, CellConstraints.FILL
  };

  @Override
  void readLayout(Element element, LwContainer container) {
    FormLayout layout = new FormLayout();
    final List<Element> rowSpecs = element.getChildren(UIFormXmlConstants.ELEMENT_ROWSPEC, element.getNamespace());
    for (Element rowSpecElement : rowSpecs) {
      final String spec = LwXmlReader.getRequiredString(rowSpecElement, UIFormXmlConstants.ATTRIBUTE_VALUE);
      layout.appendRow(new RowSpec(spec));
    }

    final List<Element> colSpecs = element.getChildren(UIFormXmlConstants.ELEMENT_COLSPEC, element.getNamespace());
    for (Element colSpecElement : colSpecs) {
      final String spec = LwXmlReader.getRequiredString(colSpecElement, UIFormXmlConstants.ATTRIBUTE_VALUE);
      layout.appendColumn(new ColumnSpec(spec));
    }

    int[][] rowGroups = readGroups(element, UIFormXmlConstants.ELEMENT_ROWGROUP);
    int[][] colGroups = readGroups(element, UIFormXmlConstants.ELEMENT_COLGROUP);
    if (rowGroups != null) {
      layout.setRowGroups(rowGroups);
    }
    if (colGroups != null) {
      layout.setColumnGroups(colGroups);
    }
    container.setLayout(layout);
  }

  private static int[][] readGroups(final Element element, final String elementName) {
    final List<Element> groupElements = element.getChildren(elementName, element.getNamespace());
    if (groupElements.isEmpty()) return null;
    int[][] groups = new int[groupElements.size()][];
    for(int i=0; i<groupElements.size(); i++) {
      Element groupElement = groupElements.get(i);
      List<Element> groupMembers = groupElement.getChildren(UIFormXmlConstants.ELEMENT_MEMBER, element.getNamespace());
      groups [i] = new int[groupMembers.size()];
      for(int j=0; j<groupMembers.size(); j++) {
        groups [i][j] = LwXmlReader.getRequiredInt(groupMembers.get(j), UIFormXmlConstants.ATTRIBUTE_INDEX);
      }
    }
    return groups;
  }

  @Override
  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
    super.readChildConstraints(constraintsElement, component);
    CellConstraints cc = new CellConstraints();
    final Element formsElement = LwXmlReader.getChild(constraintsElement, UIFormXmlConstants.ELEMENT_FORMS);
    if (formsElement != null) {
      if (formsElement.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_TOP) != null) {
        cc.insets = LwXmlReader.readInsets(formsElement);
      }
      if (!LwXmlReader.getOptionalBoolean(formsElement, UIFormXmlConstants.ATTRIBUTE_DEFAULTALIGN_HORZ, true)) {
        cc.hAlign = ourHorizontalAlignments [Utils.alignFromConstraints(component.getConstraints(), true)];
      }
      if (!LwXmlReader.getOptionalBoolean(formsElement, UIFormXmlConstants.ATTRIBUTE_DEFAULTALIGN_VERT, true)) {
        cc.vAlign = ourVerticalAlignments [Utils.alignFromConstraints(component.getConstraints(), false)];
      }
    }
    component.setCustomLayoutConstraints(cc);
  }
}
