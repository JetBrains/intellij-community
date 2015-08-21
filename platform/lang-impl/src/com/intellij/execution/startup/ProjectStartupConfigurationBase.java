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
package com.intellij.execution.startup;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupConfigurationBase implements PersistentStateComponent<Element> {
  protected final static String TOP_ELEMENT = "startup-tasks";
  private final static String TASK = "task";
  private final static String NAME = "name";
  private final static String ID = "id";

  private final List<ConfigurationDescriptor> myList;

  protected ProjectStartupConfigurationBase() {
    myList = new ArrayList<ConfigurationDescriptor>();
  }

  @Nullable
  @Override
  public Element getState() {
    if (myList.isEmpty()) return null;
    final Element element = new Element(TOP_ELEMENT);
    for (ConfigurationDescriptor descriptor : myList) {
      final Element child = new Element(TASK);
      child.setAttribute(NAME, descriptor.getName());
      child.setAttribute(ID, descriptor.getId());

      element.addContent(child);
    }
    return element;
  }

  @Override
  public void loadState(Element state) {
    myList.clear();
    final List<Element> children = state.getChildren();
    for (Element child : children) {
      if (TASK.equals(child.getName())) {
        final String name = child.getAttributeValue(NAME);
        final String id = child.getAttributeValue(ID);
        if (! StringUtil.isEmptyOrSpaces(name) && ! StringUtil.isEmptyOrSpaces(id)) {
          myList.add(new ConfigurationDescriptor(id, name));
        }
      }
    }
  }

  public void clear() {
    myList.clear();
  }

  public List<ConfigurationDescriptor> getList() {
    return myList;
  }

  public void setList(@NotNull final List<ConfigurationDescriptor> list) {
    myList.clear();
    myList.addAll(list);
  }

  public boolean isEmpty() {
    return myList.isEmpty();
  }

  public static class ConfigurationDescriptor {
    private final @NotNull String myId;
    private final @NotNull String myName;

    public ConfigurationDescriptor(@NotNull String id, @NotNull String name) {
      myId = id;
      myName = name;
    }

    @NotNull
    public String getId() {
      return myId;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ConfigurationDescriptor that = (ConfigurationDescriptor)o;

      if (!myId.equals(that.myId)) return false;
      if (!myName.equals(that.myName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myId.hashCode();
      result = 31 * result + myName.hashCode();
      return result;
    }
  }
}
