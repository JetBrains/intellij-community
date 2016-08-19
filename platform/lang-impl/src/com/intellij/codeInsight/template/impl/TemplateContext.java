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
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TemplateContext {
  private final Map<String, Boolean> myContextStates = ContainerUtil.newTroveMap();

  private static class ContextInterner {
    private static final Map<String, String> internMap = Arrays.stream(TemplateContextType.EP_NAME.getExtensions())
      .map(TemplateContextType::getContextId)
      .collect(Collectors.toMap(Function.identity(), Function.identity()));
  }

  public TemplateContext createCopy()  {
    TemplateContext cloneResult = new TemplateContext();
    cloneResult.myContextStates.putAll(myContextStates);
    return cloneResult;
  }

  @Nullable
  TemplateContextType getDifference(@NotNull TemplateContext defaultContext) {
    return ContainerUtil.find(TemplateManagerImpl.getAllContextTypes(), type -> isEnabled(type) != defaultContext.isEnabled(type));
  }

  public boolean isEnabled(@NotNull TemplateContextType contextType) {
    synchronized (myContextStates) {
      Boolean storedValue = getOwnValue(contextType);
      if (storedValue == null) {
        TemplateContextType baseContextType = contextType.getBaseContextType();
        return baseContextType != null && isEnabled(baseContextType);
      }
      return storedValue.booleanValue();
    }
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
  @VisibleForTesting
  public void setDefaultContext(@NotNull TemplateContext defContext) {
    HashMap<String, Boolean> copy = new HashMap<>(myContextStates);
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
        myContextStates.put(ContainerUtil.getOrElse(ContextInterner.internMap, name, name), Boolean.parseBoolean(value));
      }
    }

    myContextStates.putAll(makeInheritanceExplicit());
  }

  /**
   * Mark contexts explicitly as excluded which are excluded because some of their bases is explicitly marked as excluded.
   * Otherwise that `excluded` status will be forgotten if the base context is enabled.
   */
  @NotNull
  private Map<String, Boolean> makeInheritanceExplicit() {
    Map<String, Boolean> explicitStates = ContainerUtil.newHashMap();
    for (TemplateContextType type : ContainerUtil.filter(TemplateManagerImpl.getAllContextTypes(), this::isDisabledByInheritance)) {
      explicitStates.put(type.getContextId(), false);
    }
    return explicitStates;
  }

  private boolean isDisabledByInheritance(TemplateContextType type) {
    return !hasOwnValue(type) &&
           !isEnabled(type) &&
           JBIterable.generate(type, TemplateContextType::getBaseContextType).filter(this::hasOwnValue).first() != null;
  }

  private boolean hasOwnValue(TemplateContextType t) {
    return getOwnValue(t) != null;
  }

  @VisibleForTesting
  public void writeTemplateContext(Element element) throws WriteExternalException {
    for (TemplateContextType type : TemplateManagerImpl.getAllContextTypes()) {
      Boolean ownValue = getOwnValue(type);
      if (ownValue != null) {
        TemplateContextType base = type.getBaseContextType();
        boolean baseEnabled = base != null && isEnabled(base);
        if (ownValue != baseEnabled) {
          Element optionElement = new Element("option");
          optionElement.setAttribute("name", type.getContextId());
          optionElement.setAttribute("value", ownValue.toString());
          element.addContent(optionElement);
        }
      }
    }
  }

  @Override
  public String toString() {
    return myContextStates.toString();
  }
}
