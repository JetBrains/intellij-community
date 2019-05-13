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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiNameHelper;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class NameEnvironment extends UserDataHolderBase {
  static final int OBJECT_NAME = hashIdentifier("Object");
  static final int NO_NAME = 0;
  @QNameHash final static int java_lang = fromString("java.lang");
  static final QualifiedName java_lang_Enum = new QualifiedName.Interned(fromString(CommonClassNames.JAVA_LANG_ENUM));
  static final QualifiedName java_lang_annotation_Annotation =
    new QualifiedName.Interned(fromString(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION));

  static @QNameHash int fromString(String s) {
    int id = 0;
    for (int shortName : hashQualifiedName(s)) {
      id = qualifiedName(id, shortName);
    }
    return id;
  }

  static int hashIdentifier(@Nullable String s) {
    if (StringUtil.isEmpty(s)) return 0;

    // not using String.hashCode because this way there's less collisions for short package names like 'com'
    int hash = 0;
    for (int i = 0; i < s.length(); i++) {
      hash = hash * 239 + s.charAt(i);
    }
    return hash == 0 ? 1 : hash;
  }

  static int[] hashQualifiedName(@NotNull String qName) {
    qName = PsiNameHelper.getQualifiedClassName(qName, true);
    if (qName.isEmpty()) return ArrayUtil.EMPTY_INT_ARRAY;

    List<String> components = StringUtil.split(qName, ".");
    int[] result = new int[components.size()];
    for (int i = 0; i < components.size(); i++) {
      result[i] = hashIdentifier(components.get(i));
    }
    return result;
  }

  static int memberQualifiedName(@QNameHash int ownerName, @ShortName int name) {
    return name == NO_NAME || ownerName == 0 ? 0 : qualifiedName(ownerName, name);
  }

  static @QNameHash int qualifiedName(@QNameHash int prefix, @ShortName int shortName) {
    int hash = prefix * 31 + shortName;
    return hash == 0 ? 1 : hash;
  }

}
