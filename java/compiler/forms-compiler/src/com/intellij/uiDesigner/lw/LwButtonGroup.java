/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author yole
 */
public class LwButtonGroup implements IButtonGroup {
  private String myName;
  private final ArrayList myComponentIds = new ArrayList();
  private boolean myBound;

  public void read(final Element element) {
    myName = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_NAME);
    myBound = LwXmlReader.getOptionalBoolean(element, UIFormXmlConstants.ATTRIBUTE_BOUND, false);
    for(Iterator i=element.getChildren().iterator(); i.hasNext();){
      final Element child = (Element)i.next();
      myComponentIds.add(child.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_ID));
    }
  }

  public String getName() {
    return myName;
  }

  public String[] getComponentIds() {
    return (String[])myComponentIds.toArray(new String[0]);
  }

  public boolean isBound() {
    return myBound;
  }
}
