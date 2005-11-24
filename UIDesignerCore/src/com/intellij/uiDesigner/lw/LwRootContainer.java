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
import org.jdom.Element;

import javax.swing.*;


/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class LwRootContainer extends LwContainer implements IRootContainer{
  private String myClassToBind;
  private String myMainComponentBinding;
  private String myLayoutManager;

  public LwRootContainer() throws Exception{
    super(JPanel.class.getName());
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

  public String getLayoutManager() {
    return myLayoutManager;
  }

  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    if (element == null) {
      throw new IllegalArgumentException("element cannot be null");
    }
    if(!"form".equals(element.getName())){
      throw new IllegalArgumentException("unexpected element: "+element);
    }
    
    if (!Utils.FORM_NAMESPACE.equals(element.getNamespace().getURI())) {
      throw new AlienFormFileException();
    }

    setId("root");

    myClassToBind = element.getAttributeValue("bind-to-class");
    myLayoutManager = element.getAttributeValue("layout-manager");
    
    // Constraints and properties
    readChildrenImpl(element, provider);

    myMainComponentBinding = element.getAttributeValue("stored-main-component-binding");
  }
}
