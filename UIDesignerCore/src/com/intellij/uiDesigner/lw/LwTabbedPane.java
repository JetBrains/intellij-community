/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LwTabbedPane extends LwContainer{
  public LwTabbedPane() throws Exception{
    super(JTabbedPane.class.getName());
  }

  protected AbstractLayout createInitialLayout(){
    return null;
  }

  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    readId(element);
    readBinding(element);

    // Constraints and properties
    readConstraints(element);
    readProperties(element, provider);

    // Border
    readBorder(element);
    
    readChildren(element, provider);
  }

  protected void readConstraintsForChild(final Element element, final LwComponent component) {
    final Element constraintsElement = LwXmlReader.getRequiredChild(element, "constraints");
    final Element tabbedPaneChild = LwXmlReader.getRequiredChild(constraintsElement, "tabbedpane");

    final StringDescriptor descriptor = LwXmlReader.getStringDescriptor(tabbedPaneChild,
                                                                        UIFormXmlConstants.ATTRIBUTE_TITLE,
                                                                        UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                                                        UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);
    if (descriptor == null) {
      throw new IllegalArgumentException("String descriptor value required");
    }
    component.setCustomLayoutConstraints(new Constraints(descriptor));
  }

  public static final class Constraints {
    /**
     * never null
     */ 
    public final StringDescriptor myTitle;

    public Constraints(final StringDescriptor title){
      if (title == null){
        throw new IllegalArgumentException("title cannot be null");
      }
      myTitle = title;
    }
  }
}
