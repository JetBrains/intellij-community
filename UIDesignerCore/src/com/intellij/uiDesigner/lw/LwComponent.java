package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.core.GridConstraints;
import org.jdom.Element;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;  // [stathik] moved back

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

  public LwComponent(final String className){
    if (className == null){
      throw new IllegalArgumentException("className cannot be null");
    }
    myBounds = new Rectangle();
    myConstraints = new GridConstraints();
    myIntrospectedProperty2Value = new HashMap();
    myClassName = className;
    myClientProperties = new HashMap();
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

  public final LwContainer getParent(){
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
   * Class validation is performed with {@link com.intellij.uiDesigner.compiler.Utils#validateJComponentClass(java.lang.ClassLoader, java.lang.String)}
   */
  public final Element getErrorComponentProperties(){
    return myErrorComponentProperties;
  }

  public final LwIntrospectedProperty[] getAssignedIntrospectedProperties() {
    final LwIntrospectedProperty[] properties = new LwIntrospectedProperty[myIntrospectedProperty2Value.size()];
    final Iterator iterator = myIntrospectedProperty2Value.keySet().iterator();
    for (int i=0; iterator.hasNext(); i++) {
      properties[i] = (LwIntrospectedProperty)iterator.next();
    }
    return properties;
  }

  /**
   * 'id' is required attribute
   */
  protected final void readId(final Element element){
    final String id = LwXmlReader.getRequiredString(element, "id");
    setId(id);
  }

  /**
   * 'binding' is optional attribute
   */
  protected final void readBinding(final Element element){
    final String binding = element.getAttributeValue("binding");
    setBinding(binding);
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
    
    Element propertiesElement = LwXmlReader.getChild(element, "properties");
    if(propertiesElement == null){
      propertiesElement = new Element("properties", element.getNamespace());
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
}