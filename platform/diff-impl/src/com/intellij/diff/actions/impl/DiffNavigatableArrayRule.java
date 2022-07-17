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
package com.intellij.diff.actions.impl;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * {@link DiffDataKeys#NAVIGATABLE_ARRAY}
 */
public class DiffNavigatableArrayRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final Navigatable element = DiffDataKeys.NAVIGATABLE.getData(dataProvider);
    if (element == null) {
      return null;
    }

    return new Navigatable[]{element};
  }
}

