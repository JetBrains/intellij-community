package com.intellij.uiDesigner.lw;

import org.jdom.Element;

/**
 * @author Vladimir Kondratyev
 */
public final class LwRbIntroStringProperty extends LwIntrospectedProperty {
  public LwRbIntroStringProperty(final String name){
    super(name, String.class.getName());
  }

  /**
   * @return instance of {@link com.intellij.uiDesigner.lw.StringDescriptor}
   */
  public Object read(final Element element) throws Exception{
    final String value = element.getAttributeValue("value");
    if(value != null){ // direct value is specified
      return StringDescriptor.create(value);
    }
    else{ // in that case both "resource-bundle" and "key" should be specified
      final String rbName = LwXmlReader.getRequiredString(element, "resource-bundle");
      final String key = LwXmlReader.getRequiredString(element, "key");
      return new StringDescriptor(rbName, key);
    }
  }
}

