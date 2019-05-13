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
package com.intellij.framework.impl;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.FrameworkTypeEx;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class FrameworkTypeUtil {
  public static final Comparator<FrameworkType> FRAMEWORK_TYPE_COMPARATOR =
    (o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName());

  public static Map<String, FrameworkType> computeFrameworkTypeByIdMap() {
    Map<String, FrameworkType> frameworkTypes = new HashMap<>();
    for (FrameworkTypeEx type : FrameworkTypeEx.EP_NAME.getExtensions()) {
      frameworkTypes.put(type.getId(), type);
    }
    return frameworkTypes;
  }
}
