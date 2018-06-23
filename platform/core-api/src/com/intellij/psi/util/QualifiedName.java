/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class QualifiedName implements Comparable<QualifiedName> {
  @NotNull private final List<String> myComponents;

  private QualifiedName(int count) {
    myComponents = new ArrayList<>(count);
  }

  public static QualifiedName fromComponents(Collection<String> components) {
    for (String component : components) {
      assertNoDots(component);
    }
    QualifiedName qName = new QualifiedName(components.size());
    qName.myComponents.addAll(components);
    return qName;
  }

  @NotNull
  public static QualifiedName fromComponents(String... components) {
    for (String component : components) {
      assertNoDots(component);
    }
    QualifiedName result = new QualifiedName(components.length);
    Collections.addAll(result.myComponents, components);
    return result;
  }

  public QualifiedName append(String name) {
    QualifiedName result = new QualifiedName(myComponents.size()+1);
    result.myComponents.addAll(myComponents);
    result.myComponents.add(name);
    return result;
  }

  public QualifiedName append(QualifiedName qName) {
    QualifiedName result = new QualifiedName(myComponents.size()+qName.getComponentCount());
    result.myComponents.addAll(myComponents);
    result.myComponents.addAll(qName.getComponents());
    return result;
  }

  @NotNull
  public QualifiedName removeLastComponent() {
    return removeTail(1);
  }

  @NotNull
  public QualifiedName removeTail(int count) {
    int size = myComponents.size();
    QualifiedName result = new QualifiedName(size);
    result.myComponents.addAll(myComponents);
    for (int i = 0; i < count && !result.myComponents.isEmpty(); i++) {
      result.myComponents.remove(result.myComponents.size()-1);
    }
    return result;
  }

  @NotNull
  public QualifiedName removeHead(int count) {
    int size = myComponents.size();
    QualifiedName result = new QualifiedName(size);
    result.myComponents.addAll(myComponents);
    for (int i = 0; i < count && !result.myComponents.isEmpty(); i++) {
      result.myComponents.remove(0);
    }
    return result;
  }

  @NotNull
  public List<String> getComponents() {
    return myComponents;
  }

  public int getComponentCount() {
    return myComponents.size();
  }

  public boolean matches(String... components) {
    if (myComponents.size() != components.length) {
      return false;
    }
    for (int i = 0; i < myComponents.size(); i++) {
      if (!myComponents.get(i).equals(components[i])) {
        return false;
      }
    }
    return true;
  }

  public boolean matchesPrefix(QualifiedName prefix) {
    if (getComponentCount() < prefix.getComponentCount()) {
      return false;
    }
    for (int i = 0; i < prefix.getComponentCount(); i++) {
      final String component = getComponents().get(i);
      if (component == null || !component.equals(prefix.getComponents().get(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean endsWith(@NotNull String suffix) {
    return suffix.equals(getLastComponent());
  }

  public static void serialize(@Nullable QualifiedName qName, StubOutputStream dataStream) throws IOException {
    if (qName == null) {
      dataStream.writeVarInt(0);
    }
    else {
      dataStream.writeVarInt(qName.getComponentCount());
      for (String s : qName.myComponents) {
        dataStream.writeName(s);
      }
    }
  }

  @Nullable
  public static QualifiedName deserialize(StubInputStream dataStream) throws IOException {
    QualifiedName qName;
    int size = dataStream.readVarInt();
    if (size == 0) {
      qName = null;
    }
    else {
      qName = new QualifiedName(size);
      for (int i = 0; i < size; i++) {
        qName.myComponents.add(dataStream.readNameString());
      }
    }
    return qName;
  }

  @Nullable
  public String getFirstComponent() {
    if (myComponents.isEmpty()) {
      return null;
    }
    return myComponents.get(0);
  }

  @Nullable
  public String getLastComponent() {
    if (myComponents.isEmpty()) {
      return null;
    }
    return myComponents.get(myComponents.size()-1);
  }

  @Override
  public String toString() {
    return join(".");
  }

  public String join(final String separator) {
    return StringUtil.join(myComponents, separator);
  }

  @NotNull
  public static QualifiedName fromDottedString(@NotNull String refName) {
    return fromComponents(refName.split("\\."));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    QualifiedName that = (QualifiedName)o;
    return myComponents.equals(that.myComponents);
  }

  @Override
  public int hashCode() {
    return myComponents.hashCode();
  }

  public QualifiedName subQualifiedName(int fromIndex, int toIndex) {
    return fromComponents(myComponents.subList(fromIndex, toIndex));
  }

  @Override
  public int compareTo(@NotNull QualifiedName other) {
    return toString().compareTo(other.toString());
  }

  private static void assertNoDots(@NotNull String component) {
    if (component.contains(".")) {
      throw new IllegalArgumentException("Components of QualifiedName cannot contain dots inside them, but got: " + component);
    }
  }
}
