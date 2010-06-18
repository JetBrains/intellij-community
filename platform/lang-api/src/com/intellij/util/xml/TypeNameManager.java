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

package com.intellij.util.xml;

import com.intellij.ide.TypeNameEP;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeNameManager {
  public static final Map<Class, String> ourTypeNames = new HashMap<Class, String>();
  public static final List<Function<Class, String>> ourTypeProviders = new ArrayList<Function<Class, String>>();

  private TypeNameManager() {
  }

  public static void registerTypeProvider(Function<Class, String> function) { ourTypeProviders.add(function); }

  public static String getTypeName(Class aClass) {
    String s = _getTypeName(aClass);
    if (s != null) return s;
    return getDefaultTypeName(aClass);
  }

  public static String getDefaultTypeName(final Class aClass) {
    String simpleName = aClass.getSimpleName();
    final int i = simpleName.indexOf('$');
    if (i >= 0) {
      simpleName = simpleName.substring(i + 1);
    }
    return StringUtil.capitalizeWords(StringUtil.join(NameUtil.nameToWords(simpleName),  " "), true);
  }

  @Nullable
  public static <T> T getFromClassMap(Map<Class,T> map, Class value) {
    for (final Map.Entry<Class, T> entry : map.entrySet()) {
      if (entry.getKey().isAssignableFrom(value)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Nullable
  public static String _getTypeName(final Class aClass) {
    for (final Function<Class, String> function : ourTypeProviders) {
      final String s = function.fun(aClass);
      if (s != null) {
        return s;
      }
    }
    for(TypeNameEP typeNameEP: Extensions.getExtensions(TypeNameEP.EP_NAME)) {
      String s = typeNameEP.getTypeName(aClass);
      if (s != null) {
        return s;
      }
    }

    return getFromClassMap(ourTypeNames, aClass);
  }

  public static void registerTypeName(Class aClass, @NonNls String typeName) { ourTypeNames.put(aClass, typeName); }
}
