/*
 * Copyright 2000-2015 JetBrains s.r.o.
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


import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TemplateContext {
  private final Map<String, Boolean> myContextStates = ContainerUtil.newLinkedHashMap();

  public TemplateContext createCopy()  {
    TemplateContext cloneResult = new TemplateContext();
    cloneResult.myContextStates.putAll(myContextStates);
    return cloneResult;
  }

  Map<TemplateContextType, Boolean> getDifference(@Nullable TemplateContext defaultContext) {
    Map<TemplateContextType, Boolean> result = ContainerUtil.newLinkedHashMap();
    synchronized (myContextStates) {
      //noinspection NestedSynchronizedStatement
      synchronized (defaultContext == null ? myContextStates : defaultContext.myContextStates) {
        for (TemplateContextType contextType : TemplateManagerImpl.getAllContextTypes()) {
          Boolean ownValue = getOwnValue(contextType);
          if (ownValue != null && shouldSaveContextValue(defaultContext, contextType, ownValue)) {
            result.put(contextType, ownValue);
          }
        }
      }
    }
    return result;
  }

  private boolean shouldSaveContextValue(@Nullable TemplateContext defaultContext, @NotNull TemplateContextType contextType, boolean enabled) {
    if (defaultContext == null) {
      TemplateContextType base = contextType.getBaseContextType();
      return base == null ? enabled : enabled != isEnabled(base);
    }
    return enabled != defaultContext.isEnabled(contextType);
  }

  public boolean isEnabled(@NotNull TemplateContextType contextType) {
    synchronized (myContextStates) {
      Boolean storedValue = getOwnValue(contextType);
      if (storedValue == null) {
        TemplateContextType baseContextType = contextType.getBaseContextType();
        if (baseContextType != null && !(baseContextType instanceof EverywhereContextType)) {
          return isEnabled(baseContextType);
        }
        return false;
      }
      return storedValue.booleanValue();
    }
  }

  public void putValue(TemplateContextType context, boolean enabled) {
    synchronized (myContextStates) {
      myContextStates.put(context.getContextId(), enabled);
    }
  }

  public boolean isExplicitlyEnabled(TemplateContextType contextType) {
    return Boolean.TRUE.equals(getOwnValue(contextType));
  }

  @Nullable
  public Boolean getOwnValue(TemplateContextType contextType) {
    synchronized (myContextStates) {
      return myContextStates.get(contextType.getContextId());
    }
  }

  public void setEnabled(TemplateContextType contextType, boolean value) {
    synchronized (myContextStates) {
      myContextStates.put(contextType.getContextId(), value);
    }
  }

  // used during initialization => no sync
  void setDefaultContext(@NotNull TemplateContext defContext) {
    HashMap<String, Boolean> copy = new HashMap<String, Boolean>(myContextStates);
    myContextStates.clear();
    myContextStates.putAll(defContext.myContextStates);
    myContextStates.putAll(copy);
  }

  // used during initialization => no sync
  @VisibleForTesting
  public void readTemplateContext(Element element) {
    for (Element option : element.getChildren("option")) {
      String name = option.getAttributeValue("name");
      String value = option.getAttributeValue("value");
      if (name != null && value != null) {
        myContextStates.put(name, Boolean.parseBoolean(value));
      }
    }

    Map<String, Boolean> explicitStates = ContainerUtil.newHashMap();
    for (TemplateContextType type : TemplateManagerImpl.getAllContextTypes()) {
      if (getOwnValue(type) == null) {
        Iterable<TemplateContextType> bases = JBIterable.generate(type, TemplateContextType::getBaseContextType);
        explicitStates.put(type.getContextId(), ContainerUtil.getFirstItem(ContainerUtil.mapNotNull(bases, this::getOwnValue), false));
      }
    }
    myContextStates.putAll(explicitStates);
  }

  @VisibleForTesting
  public void writeTemplateContext(Element element, @Nullable TemplateContext defaultContext) throws WriteExternalException {
    Map<TemplateContextType, Boolean> diff = getDifference(defaultContext);
    for (TemplateContextType type : diff.keySet()) {
      Element optionElement = new Element("option");
      optionElement.setAttribute("name", type.getContextId());
      optionElement.setAttribute("value", diff.get(type).toString());
      element.addContent(optionElement);
    }
  }

  @Override
  public String toString() {
    return myContextStates.toString();
  }
}
