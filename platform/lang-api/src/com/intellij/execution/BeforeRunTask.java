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

package com.intellij.execution;

import org.jdom.Element;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2009
 */
public abstract class BeforeRunTask implements Cloneable{
  private boolean myIsEnabled;

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public void setEnabled(boolean isEnabled) {
    myIsEnabled = isEnabled;
  }

  public void writeExternal(Element element) {
    element.setAttribute("enabled", String.valueOf(myIsEnabled));
  }
  
  public void readExternal(Element element) {
    String attribValue = element.getAttributeValue("enabled");
    if (attribValue == null) {
      attribValue = element.getAttributeValue("value"); // maintain compatibility with old format
    }
    myIsEnabled = Boolean.valueOf(attribValue).booleanValue();
  }

  public BeforeRunTask clone() {
    try {
      return (BeforeRunTask)super.clone();
    }
    catch (CloneNotSupportedException ignored) {
      return null;
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeforeRunTask that = (BeforeRunTask)o;

    if (myIsEnabled != that.myIsEnabled) return false;

    return true;
  }

  public int hashCode() {
    return (myIsEnabled ? 1 : 0);
  }
}
