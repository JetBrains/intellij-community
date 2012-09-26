/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;


import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.Element;

import java.util.*;

public class TemplateContext {

  private final Map<String, Boolean> myAdditionalContexts;

  public TemplateContext() {
    myAdditionalContexts = new LinkedHashMap<String, Boolean>();

  }

  public TemplateContext createCopy()  {
    TemplateContext cloneResult = new TemplateContext();
    cloneResult.myAdditionalContexts.putAll(myAdditionalContexts);
    return cloneResult;
  }

  public boolean isEnabled(TemplateContextType contextType) {
    Boolean storedValue;
    synchronized (myAdditionalContexts) {
      storedValue = myAdditionalContexts.get(contextType.getContextId());
    }
    if (storedValue == null) {
      TemplateContextType baseContextType = contextType.getBaseContextType();
      if (baseContextType != null && !(baseContextType instanceof EverywhereContextType)) {
        return isEnabled(baseContextType);
      }
      return false;
    }
    return storedValue.booleanValue();
  }

  public void setEnabled(TemplateContextType contextType, boolean value) {
    synchronized (myAdditionalContexts) {
      myAdditionalContexts.put(contextType.getContextId(), value);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    List options = element.getChildren("option");
    for (Object e : options) {
      if (e instanceof Element) {
        Element option = (Element)e;
        String name = option.getAttributeValue("name");
        String value = option.getAttributeValue("value");
        if (name != null && value != null) {
          myAdditionalContexts.put(name, Boolean.parseBoolean(value));
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    List<Element> options = new ArrayList<Element>(myAdditionalContexts.size());
    for (String contextName : myAdditionalContexts.keySet()) {
      String value = myAdditionalContexts.get(contextName).toString();

      Element optionElement = new Element("option");
      optionElement.setAttributes(Arrays.asList(new Attribute("name", contextName), new Attribute("value", value)));
      options.add(optionElement);
    }
    element.setContent(options);
  }
}
