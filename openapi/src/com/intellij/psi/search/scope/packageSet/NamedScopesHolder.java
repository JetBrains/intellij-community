/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class NamedScopesHolder implements JDOMExternalizable {
  private List<NamedScope> myScopes = new ArrayList<NamedScope>();

  public NamedScope[] getScopes() {
    return myScopes.toArray(new NamedScope[myScopes.size()]);
  }

  public void removeAllSets() {
    myScopes.clear();
  }

  public void setScopes(NamedScope[] scopes) {
    myScopes = new ArrayList<NamedScope>(Arrays.asList(scopes));
  }

  public void addScope(NamedScope scope) {
    myScopes.add(scope);
  }

  private Element writeScope(NamedScope scope) {
    Element setElement = new Element("scope");
    setElement.setAttribute("name", scope.getName());
    setElement.setAttribute("pattern", scope.getValue().getText());
    return setElement;
  }

  private NamedScope readScope(Element setElement) throws ParsingException {
    PackageSet set = PackageSetFactory.getInstance().compile(setElement.getAttributeValue("pattern"));
    String name = setElement.getAttributeValue("name");
    return new NamedScope(name, set);
  }

  public void readExternal(Element element) throws InvalidDataException {
    List sets = element.getChildren("scope");
    for (int i = 0; i < sets.size(); i++) {
      try {
        addScope(readScope((Element)sets.get(i)));
      }
      catch (ParsingException e) {
        // Skip damaged set
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (int i = 0; i < myScopes.size(); i++) {
      element.addContent(writeScope(myScopes.get(i)));
    }
  }

  public NamedScope getScope(String name) {
    for (int i = 0; i < myScopes.size(); i++) {
      NamedScope scope = myScopes.get(i);
      if (name.equals(scope.getName())) return scope;
    }
    return null;
  }
}