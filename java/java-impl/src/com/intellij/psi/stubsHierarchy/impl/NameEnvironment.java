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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class NameEnvironment extends UserDataHolderBase {

  public final QualifiedName empty;
  public final QualifiedName java_lang_Object;
  public final QualifiedName java_lang_Enum;
  public final QualifiedName[] annotation;
  public final NamesEnumerator myNamesEnumerator;

  public NameEnvironment() {
    myNamesEnumerator = new NamesEnumerator();
    empty = myNamesEnumerator.getFullName(new int[]{}, true);
    java_lang_Object = fromString("java.lang.Object", true);
    java_lang_Enum = fromString("java.lang.Enum", true);
    annotation = new QualifiedName[]{fromString("java.lang.annotation.Annotation", true)};
  }

  @Nullable
  public QualifiedName fromString(String s, boolean create) {
    List<String> comps = StringUtil.split(s, ".");
    int[] ids = new int[comps.size()];
    for (int i = 0; i < comps.size(); i++) {
      int name = simpleName(comps.get(i), create);
      if (name == NamesEnumerator.NO_NAME) {
        return null;
      }
      ids[i] = name;
    }
    return myNamesEnumerator.getFullName(ids, create);
  }

  public int simpleName(String s, boolean create) {
    if (s == null)
      return NamesEnumerator.NO_NAME;
    return myNamesEnumerator.getSimpleName(s, create);
  }

  public QualifiedName prefix(QualifiedName name) {
    if (name.myComponents.length <= 1) {
      return empty;
    }
    return myNamesEnumerator.getFullName(Arrays.copyOf(name.myComponents, name.myComponents.length - 1), true);
  }

  public QualifiedName qualifiedName(int id) {
    return myNamesEnumerator.qualifiedName(id);
  }

  public int shortName(QualifiedName name) {
    int[] ids = name.myComponents;
    return ids[ids.length - 1];
  }

  public QualifiedName qualifiedName(Symbol owner, int shortName) {
    if (shortName == NamesEnumerator.NO_NAME || owner == null || owner.myQualifiedName == null) {
      return null;
    }
    return qualifiedName(owner.myQualifiedName, shortName, true);
  }

  public QualifiedName qualifiedName(QualifiedName prefix, int shortName, boolean create) {
    if (shortName == NamesEnumerator.NO_NAME)
      return null;
    if (prefix == null || prefix.isEmpty())
      return myNamesEnumerator.getFullName(new int[]{shortName}, create);

    int[] ids = Arrays.copyOf(prefix.myComponents, prefix.myComponents.length + 1);
    ids[ids.length - 1] = shortName;
    return myNamesEnumerator.getFullName(ids, create);
  }

  QualifiedName concat(int[] ids, boolean create) {
    return myNamesEnumerator.getFullName(ids, create);
  }
}
