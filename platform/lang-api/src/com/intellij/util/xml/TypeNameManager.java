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

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @deprecated this should not be used
 * @see TypePresentationService
 * @see com.intellij.ide.TypeNameEP
 * @see @Presentation
 */
public class TypeNameManager {
  private TypeNameManager() {
  }

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

    final String name = TypePresentationService.getService().getTypePresentableName(aClass);
    if (name != null) {
      return name;
    }
    return null;
  }
}
