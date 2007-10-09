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

package com.intellij.openapi.deployment;

import gnu.trove.TObjectHashingStrategy;

public class ElementIgnoringAttributesEquality implements TObjectHashingStrategy<ContainerElement> {
  public boolean equals(ContainerElement object, ContainerElement object1) {
    if (object1 == null || object == null) {
      return object == object1;
    }
    return object.equalsIgnoreAttributes(object1);
  }

  public int computeHashCode(ContainerElement object) {
    String presentableName = object.getPresentableName();
    if (presentableName == null) {
      return 0;
    }
    return presentableName.hashCode();
  }
}