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


import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.codeInsight.template.TemplateContextType;
import org.jdom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    return storedValue == null ? false : storedValue.booleanValue();
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
    for (String contextName : myAdditionalContexts.keySet()) {
      Element optionElement = new Element("option");
      optionElement.setAttribute("name", contextName);
      optionElement.setAttribute("value", myAdditionalContexts.get(contextName).toString());
      element.addContent(optionElement);
    }
  }
}
