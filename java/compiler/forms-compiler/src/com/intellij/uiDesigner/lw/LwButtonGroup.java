// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.util.ArrayList;

public class LwButtonGroup implements IButtonGroup {
  private String myName;
  private final ArrayList<String> myComponentIds = new ArrayList<String>();
  private boolean myBound;

  public void read(final Element element) {
    myName = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_NAME);
    myBound = LwXmlReader.getOptionalBoolean(element, UIFormXmlConstants.ATTRIBUTE_BOUND, false);
    for (final Element child : element.getChildren()) {
      myComponentIds.add(child.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_ID));
    }
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String[] getComponentIds() {
    return myComponentIds.toArray(new String[0]);
  }

  @Override
  public boolean isBound() {
    return myBound;
  }
}
