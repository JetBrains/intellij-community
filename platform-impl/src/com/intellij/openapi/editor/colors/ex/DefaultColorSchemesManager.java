package com.intellij.openapi.editor.colors.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class DefaultColorSchemesManager implements ApplicationComponent, JDOMExternalizable {
  private ArrayList mySchemes;
  @NonNls private static final String SCHEME_ELEMENT = "scheme";

  public String getComponentName() {
    return "DefaultColorSchemesManager";
  }

  public DefaultColorSchemesManager() {
    mySchemes = new ArrayList();
  }

  public static DefaultColorSchemesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DefaultColorSchemesManager.class);
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    List schemes = element.getChildren(SCHEME_ELEMENT);
    for (Iterator iterator = schemes.iterator(); iterator.hasNext();) {
      Element schemeElement = (Element) iterator.next();
      DefaultColorsScheme newScheme = new DefaultColorsScheme(this);
      newScheme.readExternal(schemeElement);
      mySchemes.add(newScheme);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  public DefaultColorsScheme[] getAllSchemes() {
    return (DefaultColorsScheme[]) mySchemes.toArray(new DefaultColorsScheme[mySchemes.size()]);
  }

  public EditorColorsScheme getScheme(String name) {
    for (int i = 0; i < mySchemes.size(); i++) {
      DefaultColorsScheme scheme = (DefaultColorsScheme) mySchemes.get(i);
      if (name.equals(scheme.getName())) return scheme;
    }

    return null;
  }
}
