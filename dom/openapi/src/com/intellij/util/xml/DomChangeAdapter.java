/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.util.xml.events.*;

/**
 * @author peter
 */
public abstract class DomChangeAdapter extends DomEventAdapter {

  protected abstract void elementChanged(DomElement element);

  public void childAdded(CollectionElementAddedEvent event) {
    elementChanged(event.getParent());
  }

  public void childRemoved(CollectionElementRemovedEvent event) {
    elementChanged(event.getParent());
  }

  public void elementChanged(ElementChangedEvent event) {
    elementChanged(event.getElement());
  }

  public void elementDefined(ElementDefinedEvent event) {
    elementChanged(event.getElement());
  }

  public void elementUndefined(ElementUndefinedEvent event) {
    elementChanged(event.getElement());
  }

  public void valueChanged(TagValueChangeEvent event) {
    elementChanged(event.getElement());
  }
}
