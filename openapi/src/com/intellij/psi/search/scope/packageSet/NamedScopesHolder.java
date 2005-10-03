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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class NamedScopesHolder implements JDOMExternalizable {
  private List<NamedScope> myScopes = new ArrayList<NamedScope>();
  @NonNls private static final String SCOPE_TAG = "scope";
  @NonNls private static final String NAME_ATT = "name";
  @NonNls private static final String PATTERN_ATT = "pattern";

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
    Element setElement = new Element(SCOPE_TAG);
    setElement.setAttribute(NAME_ATT, scope.getName());
    setElement.setAttribute(PATTERN_ATT, scope.getValue().getText());
    return setElement;
  }

  private NamedScope readScope(Element setElement) throws ParsingException {
    PackageSet set = PackageSetFactory.getInstance().compile(setElement.getAttributeValue(PATTERN_ATT));
    String name = setElement.getAttributeValue(NAME_ATT);
    return new NamedScope(name, set);
  }

  public void readExternal(Element element) throws InvalidDataException {
    List sets = element.getChildren(SCOPE_TAG);
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