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

package com.intellij.openapi.ui;

import org.jetbrains.annotations.Nullable;

public interface TestInputDialog {

  TestInputDialog DEFAULT = new TestInputDialog() {
    @Override
    public String show(String message) {
      throw new RuntimeException(message);
    }
  };
  TestInputDialog OK = new TestInputDialog() {
    @Override
    public String show(String message) {
      return "";
    }
  };

  String show(String message);

  default String show(String message, @Nullable InputValidator validator) {
    return show(message);
  }
}
