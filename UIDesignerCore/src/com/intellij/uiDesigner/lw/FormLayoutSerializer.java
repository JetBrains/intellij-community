/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.lw;

import org.jdom.Element;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.CellConstraints;
import com.intellij.uiDesigner.UIFormXmlConstants;

import java.util.List;
import java.util.Iterator;

/**
 * @author yole
 */
public class FormLayoutSerializer extends GridLayoutSerializer {
  private FormLayoutSerializer() {
  }

  public static FormLayoutSerializer INSTANCE = new FormLayoutSerializer();

  void readLayout(Element element, LwContainer container) {
    FormLayout layout = new FormLayout();
    final List rowSpecs = element.getChildren(UIFormXmlConstants.ELEMENT_ROWSPEC, element.getNamespace());
    for (Iterator iterator = rowSpecs.iterator(); iterator.hasNext();) {
      Element rowSpecElement = (Element) iterator.next();
      final String spec = LwXmlReader.getRequiredString(rowSpecElement, UIFormXmlConstants.ATTRIBUTE_VALUE);
      layout.appendRow(new RowSpec(spec));
    }

    final List colSpecs = element.getChildren(UIFormXmlConstants.ELEMENT_COLSPEC, element.getNamespace());
    for (Iterator iterator = colSpecs.iterator(); iterator.hasNext();) {
      Element colSpecElement = (Element) iterator.next();
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
    final List groupElements = element.getChildren(elementName, element.getNamespace());
    if (groupElements.size() == 0) return null;
    int[][] groups = new int[groupElements.size()][];
    for(int i=0; i<groupElements.size(); i++) {
      Element groupElement = (Element) groupElements.get(i);
      List groupMembers = groupElement.getChildren(UIFormXmlConstants.ELEMENT_MEMBER, element.getNamespace());
      groups [i] = new int[groupMembers.size()];
      for(int j=0; j<groupMembers.size(); j++) {
        groups [i][j] = LwXmlReader.getRequiredInt((Element) groupMembers.get(j), UIFormXmlConstants.ATTRIBUTE_INDEX);
      }
    }
    return groups;
  }

  void readChildConstraints(final Element constraintsElement, final LwComponent component) {
    super.readChildConstraints(constraintsElement, component);
    CellConstraints cc = new CellConstraints();
    final Element formsElement = LwXmlReader.getChild(constraintsElement, UIFormXmlConstants.ELEMENT_FORMS);
    if (formsElement != null) {
      if (formsElement.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_TOP) != null) {
        cc.insets = LwXmlReader.readInsets(formsElement);
      }
    }
    component.setCustomLayoutConstraints(cc);
  }
}
