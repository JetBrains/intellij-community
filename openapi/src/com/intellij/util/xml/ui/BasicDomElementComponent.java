package com.intellij.util.xml.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.module.Module;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 17, 2005
 */
public abstract class BasicDomElementComponent<T extends DomElement> extends AbstractDomElementComponent<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ui.editors.BasicDomElementComponent");

  public BasicDomElementComponent(T domElement) {
    super(domElement);
  }

  protected void bindProperties() {
      bindProperties(getDomElement());
  }

  protected void bindProperties(final DomElement domElement) {
    if (domElement == null) return;

    final java.util.List<DomChildrenDescription> childrenDescriptions = domElement.getGenericInfo().getChildrenDescriptions();
    for (final DomChildrenDescription description : childrenDescriptions) {
      final JComponent boundComponent = getBoundComponent(description);
      if (boundComponent != null) {
        if (description instanceof DomFixedChildDescription && DomUtil.isGenericValueType(description.getType())) {
          if ((description.getValues(domElement)).size() == 1) {
            DomUIControl control;
            final GenericDomValue element = domElement.getManager().createStableValue(new Factory<GenericDomValue>() {
              public GenericDomValue create() {
                return (GenericDomValue)description.getValues(domElement).get(0);
              }
            });
            if (boundComponent instanceof BigStringComponent) {
              control = new BigStringControl(new DomFixedWrapper(element));
            } else {
              control = DomUIFactory.createControl(element);
            }

            doBind(control, boundComponent);
          }
          else {
            //todo not bound

          }
        }
        else if (description instanceof DomCollectionChildDescription) {
          doBind(DomUIFactory.getDomUIFactory().createCollectionControl(domElement, (DomCollectionChildDescription)description), boundComponent);
        }
      }
    }
    reset();
  }

  protected void doBind(final DomUIControl control, final JComponent boundComponent) {
    control.bind(boundComponent);
    addComponent(control);
  }

  private JComponent getBoundComponent(final DomChildrenDescription description) {
    final Field[] fields = this.getClass().getDeclaredFields();
    for (Field field : fields) {
      try {
        field.setAccessible(true);

        if (convertFieldName(field.getName(), description).equals(description.getXmlElementName()) && field.get(this) instanceof JComponent)
        {
          return (JComponent)field.get(this);
        }
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  private String convertFieldName(String propertyName, final DomChildrenDescription description) {
    if (propertyName.startsWith("my")) propertyName = propertyName.substring(2);

    String convertedName = description.getDomNameStrategy(getDomElement()).convertName(propertyName);

    if (description instanceof DomCollectionChildDescription) {
      final String unpluralizedStr = StringUtil.unpluralize(convertedName);

      if (unpluralizedStr != null) return unpluralizedStr;
    }
    return convertedName;
  }

  public final Project getProject() {
    return getDomElement().getManager().getProject();
  }

  public final Module getModule() {
    return getDomElement().getModule();
  }

  protected void setEnabled(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        setEnabled(child, enabled);
      }
    }

  }
}
