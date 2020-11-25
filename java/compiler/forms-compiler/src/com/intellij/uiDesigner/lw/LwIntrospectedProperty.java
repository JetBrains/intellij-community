// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public abstract class LwIntrospectedProperty implements IProperty {

  private final String myName;
  private final String myPropertyClassName;
  private String myDeclaringClassName;

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
  @Override
  public final String getName(){
    return myName;
  }

  /**
   * @return never null
   */
  public final String getPropertyClassName(){
    return myPropertyClassName;
  }

  public final String getReadMethodName() {
    return "get" + Character.toUpperCase(myName.charAt(0)) + myName.substring(1);
  }

  public final String getWriteMethodName() {
    return "set" + Character.toUpperCase(myName.charAt(0)) + myName.substring(1);
  }

  public String getDeclaringClassName() {
    return myDeclaringClassName;
  }

  public void setDeclaringClassName(final String definingClassName) {
    myDeclaringClassName = definingClassName;
  }

  /**
   * @param element element that contains serialized property data. This element was
   * written by {@link com.intellij.uiDesigner.propertyInspector.IntrospectedProperty#write}
   * method. So {@code read} and {@code write} methods should be consistent.
   *
   * @return property value. Should never return {@code null}. For example,
   * value can be {@code java.lang.Integer} for {@code IntroIntProperty}.
   *
   */
  public abstract Object read(Element element) throws Exception;

  @Override
  public Object getPropertyValue(final IComponent component) {
    return ((LwComponent) component).getPropertyValue(this);
  }

  public String getCodeGenPropertyClassName() {
    return getPropertyClassName();
  }
}
