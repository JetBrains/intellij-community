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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import com.intellij.uiDesigner.UIFormXmlConstants;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class LwContainer extends LwComponent implements IContainer{
  // PLEASE DO NOT USE GENERICS IN THIS FILE AS IT IS USED IN JAVAC2 ANT TASK THAT SHOULD BE RUNNABLE WITH JDK 1.3

  /**
   * Children components
   */
  private final ArrayList myComponents;
  /**
   * Describes border's type. This member is never <code>null</code>
   */
  private BorderType myBorderType;
  /**
   * Border's title. If border doesn't have any title then
   * this member is <code>null</code>.
   */
  private StringDescriptor myBorderTitle;
  private AbstractLayout myLayout;

  public LwContainer(final String className){
    super(className);
    myComponents = new ArrayList();

    // By default container doesn't have any special border
    setBorderType(BorderType.NONE);

    myLayout = createInitialLayout();
  }


  protected AbstractLayout createInitialLayout(){
    return new XYLayoutManager();
  }

  public final AbstractLayout getLayout(){
    return myLayout;
  }

  public final void setLayout(final AbstractLayout layout) {
    myLayout = layout;
  }

  public final boolean isGrid(){
    return getLayout() instanceof GridLayoutManager;
  }

  public final boolean isXY(){
    return getLayout() instanceof XYLayoutManager;
  }

  /**
   * @param component component to be added.
   *
   * @exception IllegalArgumentException if <code>component</code> is <code>null</code>
   * @exception IllegalArgumentException if <code>component</code> already exist in the
   * container
   */
  public final void addComponent(final LwComponent component){
    if (component == null) {
      throw new IllegalArgumentException("component cannot be null");
    }
    if (myComponents.contains(component)) {
      throw new IllegalArgumentException("component is already added: " + component);
    }
    if (component.getParent() != null) {
      throw new IllegalArgumentException("component already added to another container");
    }

    // Attach to new parent
    myComponents.add(component);
    component.setParent(this);
  }

  public final IComponent getComponent(final int index) {
    return (IComponent)myComponents.get(index);
  }

  public final int getComponentCount() {
    return myComponents.size();
  }

  /**
   * @return border's type. The method never return <code>null</code>.
   *
   * @see BorderType
   */
  public final BorderType getBorderType(){
    return myBorderType;
  }

  /**
   * @see BorderType
   *
   * @exception IllegalArgumentException if <code>type</code>
   * is <code>null</code>
   */
  public final void setBorderType(final BorderType type){
    if(type==null){
      throw new IllegalArgumentException("type cannot be null");
    }
    myBorderType=type;
  }

  /**
   * @return border's title. If the container doesn't have any title then the
   * method returns <code>null</code>.
   */
  public final StringDescriptor getBorderTitle(){
    return myBorderTitle;
  }

  /**
   * @param title new border's title. <code>null</code> means that
   * the containr doesn't have have titled border.
   */
  public final void setBorderTitle(final StringDescriptor title){
    myBorderTitle=title;
  }

  /**
   * TODO[anton,vova] looks like it is better to pass contraints tag
   * 
   * @param element XML element which should contains 'constraints' tag
   */
  protected void readConstraintsForChild(final Element element, final LwComponent component){
    final Element constraintsElement = LwXmlReader.getRequiredChild(element, "constraints");

    // Read XY constrainst
    final Element xyElement = LwXmlReader.getChild(constraintsElement, "xy");
    if(xyElement != null){
      component.setBounds(
        new Rectangle(
          LwXmlReader.getRequiredInt(xyElement, "x"),
          LwXmlReader.getRequiredInt(xyElement, "y"),
          LwXmlReader.getRequiredInt(xyElement, "width"),
          LwXmlReader.getRequiredInt(xyElement, "height")
        )
      );
    }

    final GridConstraints constraints=new GridConstraints();

    // Read Grid constraints
    final Element gridElement = LwXmlReader.getChild(constraintsElement, "grid");
    if(gridElement != null){
      constraints.setRow(LwXmlReader.getRequiredInt(gridElement, "row"));
      constraints.setColumn(LwXmlReader.getRequiredInt(gridElement, "column"));
      constraints.setRowSpan(LwXmlReader.getRequiredInt(gridElement, "row-span"));
      constraints.setColSpan(LwXmlReader.getRequiredInt(gridElement, "col-span"));
      constraints.setVSizePolicy(LwXmlReader.getRequiredInt(gridElement, "vsize-policy"));
      constraints.setHSizePolicy(LwXmlReader.getRequiredInt(gridElement, "hsize-policy"));
      constraints.setAnchor(LwXmlReader.getRequiredInt(gridElement, "anchor"));
      constraints.setFill(LwXmlReader.getRequiredInt(gridElement, "fill"));

      // minimum size
      final Element minSizeElement = LwXmlReader.getChild(gridElement, "minimum-size");
      if (minSizeElement != null) {
        constraints.myMinimumSize.width = LwXmlReader.getRequiredInt(minSizeElement, "width");
        constraints.myMinimumSize.height = LwXmlReader.getRequiredInt(minSizeElement, "height");
      }

      // preferred size
      final Element prefSizeElement = LwXmlReader.getChild(gridElement, "preferred-size");
      if (prefSizeElement != null) {
        constraints.myPreferredSize.width = LwXmlReader.getRequiredInt(prefSizeElement, "width");
        constraints.myPreferredSize.height = LwXmlReader.getRequiredInt(prefSizeElement, "height");
      }

      // maximum size
      final Element maxSizeElement = LwXmlReader.getChild(gridElement, "maximum-size");
      if (maxSizeElement != null) {
        constraints.myMaximumSize.width = LwXmlReader.getRequiredInt(maxSizeElement, "width");
        constraints.myMaximumSize.height = LwXmlReader.getRequiredInt(maxSizeElement, "height");
      }

      component.getConstraints().restore(constraints);
    }
  }

  /**
   * 'border' is required subtag
   */
  protected final void readBorder(final Element element){
    final Element borderElement = LwXmlReader.getRequiredChild(element, "border");
    setBorderType(BorderType.valueOf(LwXmlReader.getRequiredString(borderElement, "type")));

    StringDescriptor descriptor = LwXmlReader.getStringDescriptor(borderElement,
                                                                  UIFormXmlConstants.ATTRIBUTE_TITLE,
                                                                  UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                                                  UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);
    if (descriptor != null) {
      setBorderTitle(descriptor);
    }
  }

  /**
   * 'children' is required attribute
   */
  protected final void readChildren(final Element element, final PropertiesProvider provider) throws Exception{
    final Element childrenElement = LwXmlReader.getRequiredChild(element, "children");
    readChildrenImpl(childrenElement, provider);
  }

  protected final void readChildrenImpl(final Element element, final PropertiesProvider provider) throws Exception {
    for(Iterator i=element.getChildren().iterator(); i.hasNext();){
      final Element child = (Element)i.next();
      final LwComponent component = createComponentFromTag(child);
      addComponent(component);
      component.read(child, provider);
    }
  }

  public static LwComponent createComponentFromTag(final Element child) throws Exception {
    final String name = child.getName();
    final LwComponent component;
    if("component".equals(name)){
      final String className = LwXmlReader.getRequiredString(child, "class");
      component = new LwAtomicComponent(className);
    }
    else if("vspacer".equals(name)){
      component = new LwVSpacer();
    }
    else if("hspacer".equals(name)){
      component = new LwHSpacer();
    }
    else if("xy".equals(name) || "grid".equals(name)){
      component = new LwContainer(JPanel.class.getName());
    }
    else if("scrollpane".equals(name)){
      component = new LwScrollPane();
    }
    else if("tabbedpane".equals(name)){
      component = new LwTabbedPane();
    }
    else if("splitpane".equals(name)){
      component = new LwSplitPane();
    }
    else{
      throw new IllegalArgumentException("unexpected element: "+child);
    }
    return component;
  }

  /**
   * 'xy' or 'grid'
   */
  protected final void readLayout(final Element element){
    if("xy".equals(element.getName())){
      setLayout(new XYLayoutManager());
    }
    else if("grid".equals(element.getName())){
      final int rowCount = LwXmlReader.getRequiredInt(element, "row-count");
      final int columnCount = LwXmlReader.getRequiredInt(element, "column-count");

      final int hGap = LwXmlReader.getRequiredInt(element, "hgap");
      final int vGap = LwXmlReader.getRequiredInt(element, "vgap");

      // attribute is optional for compatibility with IDEA 4.0 forms
      final boolean sameSizeHorizontally = LwXmlReader.getOptionalBoolean(element, "same-size-horizontally", false);
      final boolean sameSizeVertically = LwXmlReader.getOptionalBoolean(element, "same-size-vertically", false);

      final Element marginElement = LwXmlReader.getRequiredChild(element, "margin");
      final Insets margin = new Insets(
        LwXmlReader.getRequiredInt(marginElement,"top"),
        LwXmlReader.getRequiredInt(marginElement,"left"),
        LwXmlReader.getRequiredInt(marginElement,"bottom"),
        LwXmlReader.getRequiredInt(marginElement,"right")
      );

      final GridLayoutManager layout = new GridLayoutManager(rowCount, columnCount);
      layout.setMargin(margin);
      layout.setVGap(vGap);
      layout.setHGap(hGap);
      layout.setSameSizeHorizontally(sameSizeHorizontally);
      layout.setSameSizeVertically(sameSizeVertically);
      setLayout(layout);
    }
    else{
      throw new IllegalArgumentException("unexpected element: "+element);
    }
  }

  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    readId(element);
    readBinding(element);

    // Layout
    readLayout(element);

    // Constraints and properties
    readConstraints(element);
    readProperties(element, provider);

    // Border
    readBorder(element);
    
    readChildren(element, provider);
  }
}