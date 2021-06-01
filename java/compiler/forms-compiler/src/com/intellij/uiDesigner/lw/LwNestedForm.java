// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

public class LwNestedForm extends LwComponent {
  private String myFormFileName;

  public LwNestedForm() {
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
