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
    
    // Constraints and properties
    readChildrenImpl(element, provider);

    myMainComponentBinding = element.getAttributeValue("stored-main-component-binding");
  }
}
