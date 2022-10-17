// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.UnexpectedFormElementException;
import com.intellij.uiDesigner.compiler.Utils;
import org.jdom.Element;

import java.util.ArrayList;

/**
  */
public final class LwRootContainer extends LwContainer implements IRootContainer{
  private String myClassToBind;
  private String myMainComponentBinding;
  private final ArrayList<LwButtonGroup> myButtonGroups = new ArrayList<>();
  private final ArrayList<LwInspectionSuppression> myInspectionSuppressions = new ArrayList<>();

  public LwRootContainer() {
    super("javax.swing.JPanel");
    myLayoutSerializer = XYLayoutSerializer.INSTANCE;
  }

  public String getMainComponentBinding(){
    return myMainComponentBinding;
  }

  @Override
  public String getClassToBind(){
    return myClassToBind;
  }

  public void setClassToBind(final String classToBind) {
    myClassToBind = classToBind;
  }

  @Override
  public void read(final Element element, final PropertiesProvider provider) throws Exception {
    if (element == null) {
      throw new IllegalArgumentException("element cannot be null");
    }
    if (!Utils.FORM_NAMESPACE.equals(element.getNamespace().getURI())) {
      throw new AlienFormFileException();
    }
    if(!"form".equals(element.getName())){
      throw new UnexpectedFormElementException("unexpected element: "+element);
    }

    setId("root");

    myClassToBind = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS);

    // Constraints and properties
    for (final Element child : element.getChildren()) {
      if (child.getName().equals(UIFormXmlConstants.ELEMENT_BUTTON_GROUPS)) {
        readButtonGroups(child);
      }
      else if (child.getName().equals(UIFormXmlConstants.ELEMENT_INSPECTION_SUPPRESSIONS)) {
        readInspectionSuppressions(child);
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
    for (final Element child : element.getChildren()) {
      LwButtonGroup group = new LwButtonGroup();
      group.read(child);
      myButtonGroups.add(group);
    }
  }

  private void readInspectionSuppressions(final Element element) {
    for (final Element child : element.getChildren()) {
      String inspectionId = LwXmlReader.getRequiredString(child, UIFormXmlConstants.ATTRIBUTE_INSPECTION);
      String componentId = LwXmlReader.getString(child, UIFormXmlConstants.ATTRIBUTE_ID);
      myInspectionSuppressions.add(new LwInspectionSuppression(inspectionId, componentId));
    }
  }

  @Override
  public IButtonGroup[] getButtonGroups() {
    return myButtonGroups.toArray(new LwButtonGroup[0]);
  }

  @Override
  public String getButtonGroupName(IComponent component) {
    for (LwButtonGroup group : myButtonGroups) {
      final String[] ids = group.getComponentIds();
      for (String id : ids) {
        if (id.equals(component.getId())) {
          return group.getName();
        }
      }
    }
    return null;
  }

  @Override
  public String[] getButtonGroupComponentIds(String groupName) {
    for (LwButtonGroup group : myButtonGroups) {
      if (group.getName().equals(groupName)) {
        return group.getComponentIds();
      }
    }
    throw new IllegalArgumentException("Cannot find group " + groupName);
  }

  @Override
  public boolean isInspectionSuppressed(final String inspectionId, final String componentId) {
    for (LwInspectionSuppression suppression : myInspectionSuppressions) {
      if ((suppression.getComponentId() == null || suppression.getComponentId().equals(componentId)) &&
          suppression.getInspectionId().equals(inspectionId)) {
        return true;
      }
    }
    return false;
  }

  public LwInspectionSuppression[] getInspectionSuppressions() {
    return myInspectionSuppressions.toArray(LwInspectionSuppression.EMPTY_ARRAY);
  }
}
