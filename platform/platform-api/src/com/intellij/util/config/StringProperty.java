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

package com.intellij.util.config;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;

public class StringProperty extends ValueProperty<String> {
  public StringProperty(@NonNls String name, String defaultValue) {
    super(name, defaultValue);
  }

  public boolean areEqual(String value1, String value2) {
    return Comparing.strEqual(value1, value2, true);
  }
}
