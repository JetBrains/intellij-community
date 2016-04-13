/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

/**
* Would be deleted during 2016.2 EAP!
*/
@Deprecated
public class AbstractVariableData extends com.intellij.refactoring.util.AbstractVariableData {
  public static AbstractVariableData copy(com.intellij.refactoring.util.AbstractVariableData data) {
    final AbstractVariableData cdata = new AbstractVariableData();
    cdata.passAsParameter = data.passAsParameter;
    cdata.name = data.name;
    cdata.originalName = data.originalName;
    return cdata;
  }

  public static AbstractVariableData[] copy(com.intellij.refactoring.util.AbstractVariableData[] data) {
    final AbstractVariableData[] cdata = new AbstractVariableData[data.length];
    for (int i = 0; i < data.length; i++) {
      cdata[i] = copy(data[i]);
    }
    return cdata;
  }
}
