// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.UnexpectedFormElementException;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jdom.Element;

import java.awt.*;
import java.util.ArrayList;

public class LwContainer extends LwComponent implements IContainer{
  /**
   * Children components
   */
  private final ArrayList myComponents;
  /**
   * Describes border's type. This member is never {@code null}
   */
  private BorderType myBorderType;
  /**
   * Border's title. If border doesn't have any title then
   * this member is {@code null}.
   */
  private StringDescriptor myBorderTitle;
  private int myBorderTitleJustification;
  private int myBorderTitlePosition;
  private FontDescriptor myBorderTitleFont;
  private ColorDescriptor myBorderTitleColor;
  private Insets myBorderSize;
  private ColorDescriptor myBorderColor;
  private LayoutManager myLayout;
  private String myLayoutManager;
  LayoutSerializer myLayoutSerializer;

  public LwContainer(final String className){
    super(className);
    myComponents = new ArrayList();

    // By default container doesn't have any special border
    setBorderType(BorderType.NONE);

    myLayout = createInitialLayout();
  }


  protected LayoutManager createInitialLayout(){
    return new XYLayoutManager();
  }

  public final LayoutManager getLayout() {
    return myLayout;
  }

  public final void setLayout(final LayoutManager layout) {
    myLayout = layout;
  }

  public String getLayoutManager() {
    return myLayoutManager;
  }

  public final boolean isGrid(){
    return getLayout() instanceof GridLayoutManager;
  }

  @Override
  public final boolean isXY(){
    return getLayout() instanceof XYLayoutManager;
  }

  /**
   * @param component component to be added.
   *
   * @exception IllegalArgumentException if {@code component} is {@code null}
   * @exception IllegalArgumentException if {@code component} already exist in the
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

  @Override
  public final IComponent getComponent(final int index) {
    return (IComponent)myComponents.get(index);
  }

  @Override
  public final int getComponentCount() {
    return myComponents.size();
  }

  @Override
  public int indexOfComponent(final IComponent lwComponent) {
    return myComponents.indexOf(lwComponent);
  }

  /**
   * @return border's type. The method never return {@code null}.
   *
   * @see BorderType
   */
  @Override
  public final BorderType getBorderType(){
    return myBorderType;
  }

  @Override
  public boolean accept(ComponentVisitor visitor) {
    if (!super.accept(visitor)) {
      return false;
    }

    for (int i = 0; i < getComponentCount(); i++) {
      final IComponent c = getComponent(i);
      if (!c.accept(visitor)) {
        return false;
      }
    }

    return true;
  }

  /**
   * @see BorderType
   *
   * @exception IllegalArgumentException if {@code type}
   * is {@code null}
   */
  public final void setBorderType(final BorderType type){
    if(type==null){
      throw new IllegalArgumentException("type cannot be null");
    }
    myBorderType=type;
  }

  /**
   * @return border's title. If the container doesn't have any title then the
   * method returns {@code null}.
   */
  @Override
  public final StringDescriptor getBorderTitle(){
    return myBorderTitle;
  }

  /**
   * @param title new border's title. {@code null} means that
   * the containr doesn't have have titled border.
   */
  private void setBorderTitle(final StringDescriptor title){
    myBorderTitle=title;
  }

  public int getBorderTitleJustification() {
    return myBorderTitleJustification;
  }

  public int getBorderTitlePosition() {
    return myBorderTitlePosition;
  }

  public FontDescriptor getBorderTitleFont() {
    return myBorderTitleFont;
  }

  public ColorDescriptor getBorderTitleColor() {
    return myBorderTitleColor;
  }

  public Insets getBorderSize() {
    return myBorderSize;
  }

  public ColorDescriptor getBorderColor() {
    return myBorderColor;
  }

  /**
   * TODO[anton,vova] looks like it is better to pass contraints tag
   *
   * @param element XML element which should contains 'constraints' tag
   */
  protected void readConstraintsForChild(final Element element, final LwComponent component){
    if (myLayoutSerializer != null) {
      final Element constraintsElement = LwXmlReader.getRequiredChild(element, "constraints");
      myLayoutSerializer.readChildConstraints(constraintsElement, component);
    }
  }

  /**
   * 'border' is required subtag
   */
  private void readBorder(final Element element) {
    final Element borderElement = LwXmlReader.getRequiredChild(element, UIFormXmlConstants.ELEMENT_BORDER);
    setBorderType(BorderType.valueOf(LwXmlReader.getRequiredString(borderElement, UIFormXmlConstants.ATTRIBUTE_TYPE)));

    StringDescriptor descriptor = LwXmlReader.getStringDescriptor(borderElement,
                                                                  UIFormXmlConstants.ATTRIBUTE_TITLE,
                                                                  UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE,
                                                                  UIFormXmlConstants.ATTRIBUTE_TITLE_KEY);
    if (descriptor != null) {
      setBorderTitle(descriptor);
    }

    myBorderTitleJustification = LwXmlReader.getOptionalInt(borderElement, UIFormXmlConstants.ATTRIBUTE_TITLE_JUSTIFICATION, 0);
    myBorderTitlePosition = LwXmlReader.getOptionalInt(borderElement, UIFormXmlConstants.ATTRIBUTE_TITLE_POSITION, 0);
    Element fontElement = LwXmlReader.getChild(borderElement, UIFormXmlConstants.ELEMENT_FONT);
    if (fontElement != null) {
      myBorderTitleFont = LwXmlReader.getFontDescriptor(fontElement);
    }
    myBorderTitleColor = LwXmlReader.getOptionalColorDescriptor(LwXmlReader.getChild(borderElement, UIFormXmlConstants.ELEMENT_TITLE_COLOR));
    myBorderColor = LwXmlReader.getOptionalColorDescriptor(LwXmlReader.getChild(borderElement, UIFormXmlConstants.ELEMENT_COLOR));
    Element sizeElement = LwXmlReader.getChild(borderElement, UIFormXmlConstants.ELEMENT_SIZE);
    if (sizeElement != null) {
      try {
        myBorderSize = LwXmlReader.readInsets(sizeElement);
      }
      catch(Exception e) {
        myBorderSize = null;
      }
    }
  }

  /**
   * 'children' is required attribute
   */
  private void readChildren(final Element element, final PropertiesProvider provider) throws Exception{
    final Element childrenElement = LwXmlReader.getRequiredChild(element, "children");
    for (final Element child : childrenElement.getChildren()) {
      final LwComponent component = createComponentFromTag(child);
      addComponent(component);
      component.read(child, provider);
    }
  }

  public static LwComponent createComponentFromTag(final Element child) throws Exception {
    final String name = child.getName();
    final LwComponent component;
    if("component".equals(name)){
      final String className = LwXmlReader.getRequiredString(child, UIFormXmlConstants.ATTRIBUTE_CLASS);
      component = new LwAtomicComponent(className);
    }
    else if (UIFormXmlConstants.ELEMENT_NESTED_FORM.equals(name)) {
      component = new LwNestedForm();
    }
    else if("vspacer".equals(name)){
      component = new LwVSpacer();
    }
    else if("hspacer".equals(name)){
      component = new LwHSpacer();
    }
    else if("xy".equals(name) || "grid".equals(name)){
      String className = LwXmlReader.getOptionalString(child, UIFormXmlConstants.ATTRIBUTE_CLASS, "javax.swing.JPanel");
      component = new LwContainer(className);
    }
    else if(UIFormXmlConstants.ELEMENT_SCROLLPANE.equals(name)) {
      String className = LwXmlReader.getOptionalString(child, UIFormXmlConstants.ATTRIBUTE_CLASS, "javax.swing.JScrollPane");
      component = new LwScrollPane(className);
    }
    else if(UIFormXmlConstants.ELEMENT_TABBEDPANE.equals(name)){
      String className = LwXmlReader.getOptionalString(child, UIFormXmlConstants.ATTRIBUTE_CLASS, "javax.swing.JTabbedPane");
      component = new LwTabbedPane(className);
    }
    else if(UIFormXmlConstants.ELEMENT_SPLITPANE.equals(name)){
      String className = LwXmlReader.getOptionalString(child, UIFormXmlConstants.ATTRIBUTE_CLASS, "javax.swing.JSplitPane");
      component = new LwSplitPane(className);
    }
    else if (UIFormXmlConstants.ELEMENT_TOOLBAR.equals(name)) {
      String className = LwXmlReader.getOptionalString(child, UIFormXmlConstants.ATTRIBUTE_CLASS, "javax.swing.JToolBar");
      component = new LwToolBar(className);
    }
    else{
      throw new UnexpectedFormElementException("unexpected element: "+child);
    }
    return component;
  }

  /**
   * 'xy' or 'grid'
   */
  protected final void readLayout(final Element element){
    myLayoutManager = element.getAttributeValue("layout-manager");
    if("xy".equals(element.getName())){
      myLayoutSerializer = XYLayoutSerializer.INSTANCE;
    }
    else if("grid".equals(element.getName())){
      createLayoutSerializer();
    }
    else{
      throw new UnexpectedFormElementException("unexpected element: "+element);
    }
    myLayoutSerializer.readLayout(element, this);
  }

  public void setLayoutManager(final String layoutManager) {
    myLayoutManager = layoutManager;
    createLayoutSerializer();
  }

  private void createLayoutSerializer() {
    if (UIFormXmlConstants.LAYOUT_BORDER.equals(myLayoutManager)) {
      myLayoutSerializer = BorderLayoutSerializer.INSTANCE;
    }
    else if (UIFormXmlConstants.LAYOUT_FLOW.equals(myLayoutManager)) {
      myLayoutSerializer = FlowLayoutSerializer.INSTANCE;
    }
    else if (UIFormXmlConstants.LAYOUT_CARD.equals(myLayoutManager)) {
      myLayoutSerializer = CardLayoutSerializer.INSTANCE;
    }
    else if (UIFormXmlConstants.LAYOUT_XY.equals(myLayoutManager)) {
      myLayoutSerializer = XYLayoutSerializer.INSTANCE;
    }
    else if (UIFormXmlConstants.LAYOUT_FORM.equals(myLayoutManager)) {
      myLayoutSerializer = FormLayoutSerializer.INSTANCE;
    }
    else if (UIFormXmlConstants.LAYOUT_GRIDBAG.equals(myLayoutManager)) {
      myLayoutSerializer = GridBagLayoutSerializer.INSTANCE;
    }
    else {
      myLayoutSerializer = GridLayoutSerializer.INSTANCE;
    }
  }

  @Override
  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    readBase(element);

    // Layout
    readLayout(element);

    // Constraints and properties
    readConstraints(element);
    readProperties(element, provider);

    // Border
    readBorder(element);

    readChildren(element, provider);
  }

  void readNoLayout(final Element element, final PropertiesProvider provider) throws Exception {
    readBase(element);

    // Constraints and properties
    readConstraints(element);
    readProperties(element, provider);

    // Border
    readBorder(element);

    readChildren(element, provider);
  }

  @Override
  public boolean areChildrenExclusive() {
    return UIFormXmlConstants.LAYOUT_CARD.equals(myLayoutManager);
  }
}