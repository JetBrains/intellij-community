// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

public final class LwNestedForm extends LwComponent {
  private String myFormFileName;

  LwNestedForm() {
    super("");
  }

  @Override
  public void read(Element element, PropertiesProvider provider) throws Exception {
    myFormFileName = LwXmlReader.getRequiredString(element, UIFormXmlConstants.ATTRIBUTE_FORM_FILE);
    readBase(element);
    readConstraints(element);
  }

  public String getFormFileName() {
    return myFormFileName;
  }
}
