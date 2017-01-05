/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.uiDesigner.core.GridConstraints;
import org.jdom.Element;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class LwComponent implements IComponent{
  /**
   *  Component's ID. Cannot be null.
   */
  private String myId;
  /**
   * may be null
   */
  private String myBinding;
  /**
   * Component class
   */
  private final String myClassName;
  /**
   * Parent LwContainer. This field is always not <code>null</code>
   * is the component is in hierarchy. But the root of hierarchy
   * has <code>null</code> parent indeed.
   */
  private LwContainer myParent;
  /**
   * never <code>null</code>
   */
  private final GridConstraints myConstraints;

  private Object myCustomLayoutConstraints;
  /**
   * Bounds in XY layout
   */
  private final Rectangle myBounds;

  private final HashMap myIntrospectedProperty2Value;
  /**
   * if class is unknown (cannot be loaded), properties tag is stored as is
   */
  private Element myErrorComponentProperties;
  protected final HashMap myClientProperties;
  protected final HashMap myDelegeeClientProperties;
  private boolean myCustomCreate = false;
  private boolean myDefaultBinding = false;

  public LwComponent(final String className){
    if (className == null){
      throw new IllegalArgumentException("className cannot be null");
    }
    myBounds = new Rectangle();
    myConstraints = new GridConstraints();
    myIntrospectedProperty2Value = new LinkedHashMap();
    myClassName = className;
    myClientProperties = new LinkedHashMap();
    myDelegeeClientProperties = new LinkedHashMap();
  }

  public final String getId() {
    return myId;
  }

  public final void setId(final String id){
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }
    myId = id;
  }

  public final String getBinding(){
    return myBinding;
  }

  public final void setBinding(final String binding){
    myBinding = binding;
  }

  public final Object getCustomLayoutConstraints(){
    return myCustomLayoutConstraints;
  }

  public final void setCustomLayoutConstraints(final Object customLayoutConstraints){
    myCustomLayoutConstraints = customLayoutConstraints;
  }

  /**
   * @return never null
   */
  public final String getComponentClassName(){
    return myClassName;
  }

  public IProperty[] getModifiedProperties() {
    return getAssignedIntrospectedProperties();
  }

  /**
   * @return component's constraints in XY layout. This method rever
   * returns <code>null</code>.
   */
  public final Rectangle getBounds(){
    return (Rectangle)myBounds.clone();
  }

  /**
   * @return component's constraints in GridLayoutManager. This method never
   * returns <code>null</code>.
   */
  public final GridConstraints getConstraints(){
    return myConstraints;
  }

  public boolean isCustomCreate() {
    return myCustomCreate;
  }

  public boolean isDefaultBinding() {
    return myDefaultBinding;
  }

  public boolean accept(ComponentVisitor visitor) {
    return visitor.visit(this);
  }

  public boolean areChildrenExclusive() {
    return false;
  }

  public final LwContainer getParent(){
    return myParent;
  }

  public IContainer getParentContainer() {
    return myParent;
  }

  protected final void setParent(final LwContainer parent){
    myParent = parent;
  }

  public final void setBounds(final Rectangle bounds) {
    myBounds.setBounds(bounds);
  }

  public final Object getPropertyValue(final LwIntrospectedProperty property){
    return myIntrospectedProperty2Value.get(property);
  }

  public final void setPropertyValue(final LwIntrospectedProperty property, final Object value){
    myIntrospectedProperty2Value.put(property, value);
  }

  /**
   * @return <code>null</code> only if component class is not valid.
   * Class validation is performed with {@link com.intellij.uiDesigner.compiler.Utils#validateJComponentClass(ClassLoader,String,boolean)}
   */
  public final Element getErrorComponentProperties(){
    return myErrorComponentProperties;
  }

  public final LwIntrospectedProperty[] getAssignedIntrospectedProperties() {
    final LwIntrospectedProperty[] properties = new LwIntrospectedProperty[myIntrospectedProperty2Value.size()];
    final Iterator iterator = myIntrospectedProperty2Value.keySet().iterator();
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i=0; iterator.hasNext(); i++) {
      properties[i] = (LwIntrospectedProperty)iterator.next();
    }
    return properties;
  }

  /**
   * 'id' is required attribute
   * 'binding' is optional attribute
   */
  protected final void readBase(final Element element) {
    setId(LwXmlReader.getRequiredString(element, UIFormXmlConstants.ATTRIBUTE_ID));
    setBinding(element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_BINDING));
    myCustomCreate = LwXmlReader.getOptionalBoolean(element, UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, false);
    myDefaultBinding = LwXmlReader.getOptionalBoolean(element, UIFormXmlConstants.ATTRIBUTE_DEFAULT_BINDING, false);
  }

  /**
   * 'properties' is not required subtag
   * @param provider can be null if no properties should be read 
   */
  protected final void readProperties(final Element element, final PropertiesProvider provider) {
    if (provider == null) {
      // do not read properties 
      return;
    }

    Element propertiesElement = LwXmlReader.getChild(element, UIFormXmlConstants.ELEMENT_PROPERTIES);
    if(propertiesElement == null){
      propertiesElement = new Element(UIFormXmlConstants.ELEMENT_PROPERTIES, element.getNamespace());
    }

    final HashMap name2property = provider.getLwProperties(getComponentClassName());
    if (name2property == null) {
      myErrorComponentProperties = (Element)propertiesElement.clone();
      return;
    }

    final List propertyElements = propertiesElement.getChildren();
    for (int i = 0; i < propertyElements.size(); i++) {
      final Element t = (Element)propertyElements.get(i);
      final String name = t.getName();
      final LwIntrospectedProperty property = (LwIntrospectedProperty)name2property.get(name);
      if (property == null){
        continue;
      }
      try {
        final Object value = property.read(t);
        setPropertyValue(property, value);
      }
      catch (final Exception exc) {
        // Skip non readable properties
      }
    }

    readClientProperties(element);
  }

  private void readClientProperties(final Element element) {
    Element propertiesElement = LwXmlReader.getChild(element, UIFormXmlConstants.ELEMENT_CLIENT_PROPERTIES);
    if (propertiesElement == null) return;
    final List clientPropertyList = propertiesElement.getChildren();
    for(int i=0; i<clientPropertyList.size(); i++) {
      final Element prop = (Element) clientPropertyList.get(i);
      final String propName = prop.getName();
      final String className = LwXmlReader.getRequiredString(prop, UIFormXmlConstants.ATTRIBUTE_CLASS);

      LwIntrospectedProperty lwProp;
      if (className.equals(Integer.class.getName())) {
        lwProp = new LwIntroIntProperty(propName);
      }
      else if (className.equals(Boolean.class.getName())) {
        lwProp = new LwIntroBooleanProperty(propName);
      }
      else if (className.equals(Double.class.getName())) {
        lwProp = new LwIntroPrimitiveTypeProperty(propName, Double.class);
      }
      else {
        Class propClass;
        try {
          propClass = Class.forName(className);
        }
        catch (ClassNotFoundException e) {
          continue;
        }
        lwProp = CompiledClassPropertiesProvider.propertyFromClass(propClass, propName);
      }

      if (lwProp != null) {
        final Object value;
        try {
          value = lwProp.read(prop);
        }
        catch (Exception e) {
          continue;
        }
        myDelegeeClientProperties.put(propName, value);
      }
    }
  }

  /**
   * Delegates reading of constraints to the parent container
   */
  protected final void readConstraints(final Element element){
    final LwContainer parent = getParent();
    if(parent == null){
      throw new IllegalStateException("component must be in LW tree: "+this);
    }
    parent.readConstraintsForChild(element, this);
  }

  /**
   * @param provider can be null if no component classes should not be created
   */
  public abstract void read(Element element, PropertiesProvider provider) throws Exception;

  /**
   * @see javax.swing.JComponent#getClientProperty(Object)
   */
  public final Object getClientProperty(final Object key){
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }
    return myClientProperties.get(key);
  }

  /**
   * @see javax.swing.JComponent#putClientProperty(Object, Object)
   */
  public final void putClientProperty(final Object key, final Object value){
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }
    myClientProperties.put(key, value);
  }

  public HashMap getDelegeeClientProperties() {
    return myDelegeeClientProperties;
  }
}