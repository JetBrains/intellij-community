package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public abstract class LwIntrospectedProperty {

  private final String myName;
  private final String myPropertyClassName;

  public LwIntrospectedProperty(
    final String name,
    final String propertyClassName
  ){
    if (name == null){
      throw new IllegalArgumentException("name cannot be null");
    }
    if (propertyClassName == null){
      throw new IllegalArgumentException("propertyClassName cannot be null");
    }

    myName = name;
    myPropertyClassName = propertyClassName;
  }

  /**
   * @return never null
   */ 
  public final String getName(){
    return myName;
  }

  /**
   * @return never null
   */ 
  public final String getPropertyClassName(){
    return myPropertyClassName;
  }
  
  public final String getWriteMethodName() {
    return "set" + Character.toUpperCase(myName.charAt(0)) + myName.substring(1);
  }

  /**
   * @param element element that contains serialized property data. This element was
   * written by {@link com.intellij.uiDesigner.propertyInspector.IntrospectedProperty#write(Object, com.intellij.uiDesigner.XmlWriter)}
   * method. So <code>read</code> and <code>write</code> methods should be consistent.
   *
   * @return property value. Should never return <code>null</code>. For example,
   * value can be <code>java.lang.Integer</code> for <code>IntroIntProperty</code>.
   *
   */
  public abstract Object read(Element element) throws Exception;
}
