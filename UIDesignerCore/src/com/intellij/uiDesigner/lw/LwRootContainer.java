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

import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.compiler.UnexpectedFormElementException;
import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
  */
public final class LwRootContainer extends LwContainer implements IRootContainer{
  private String myClassToBind;
  private String myMainComponentBinding;
  private ArrayList myButtonGroups = new ArrayList();

  public LwRootContainer() throws Exception{
    super(JPanel.class.getName());
    myLayoutSerializer = XYLayoutSerializer.INSTANCE;
  }

  public String getMainComponentBinding(){
    return myMainComponentBinding;
  }

  public String getClassToBind(){
    return myClassToBind;
  }

  public void setClassToBind(final String classToBind) {
    myClassToBind = classToBind;
  }

  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    if (element == null) {
      throw new IllegalArgumentException("element cannot be null");
    }
    if(!"form".equals(element.getName())){
      throw new UnexpectedFormElementException("unexpected element: "+element);
    }

    if (!Utils.FORM_NAMESPACE.equals(element.getNamespace().getURI())) {
      throw new AlienFormFileException();
    }

    setId("root");

    myClassToBind = element.getAttributeValue("bind-to-class");

    // Constraints and properties
    for(Iterator i=element.getChildren().iterator(); i.hasNext();){
      final Element child = (Element)i.next();
      if (child.getName().equals(UIFormXmlConstants.ELEMENT_BUTTON_GROUPS)) {
        readButtonGroups(child);
      }
      else {
        final LwComponent component = createComponentFromTag(child);
        addComponent(component);
        component.read(child, provider);
      }
    }

    myMainComponentBinding = element.getAttributeValue("stored-main-component-binding");
  }

  private void readButtonGroups(final Element element) {
    for(Iterator i=element.getChildren().iterator(); i.hasNext();){
      final Element child = (Element)i.next();
      LwButtonGroup group = new LwButtonGroup();
      group.read(child);
      myButtonGroups.add(group);
    }
  }

  public LwButtonGroup[] getButtonGroups() {
    return (LwButtonGroup[])myButtonGroups.toArray(new LwButtonGroup[myButtonGroups.size()]);
  }

  public String getButtonGroupName(IComponent component) {
    for(int i=0; i<myButtonGroups.size(); i++) {
      LwButtonGroup group = (LwButtonGroup) myButtonGroups.get(i);
      final String[] ids = group.getComponentIds();
      for(int j=0; j<ids.length; j++) {
        if (ids [j].equals(component.getId())) {
          return group.getName();
        }
      }
    }
    return null;
  }

  public String[] getButtonGroupComponentIds(String groupName) {
    for(int i=0; i<myButtonGroups.size(); i++) {
      LwButtonGroup group = (LwButtonGroup) myButtonGroups.get(i);
      if (group.getName().equals(groupName)) {
        return group.getComponentIds();
      }
    }
    throw new IllegalArgumentException("Cannot find group " + groupName);
  }
}
