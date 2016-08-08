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

/*
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Jul 4, 2007
 * Time: 3:59:52 PM
 */
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface KeymapExtension {
  @NonNls ExtensionPointName<KeymapExtension> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.keymapExtension");

  @Nullable
  KeymapGroup createGroup(Condition<AnAction> filtered, Project project);
}