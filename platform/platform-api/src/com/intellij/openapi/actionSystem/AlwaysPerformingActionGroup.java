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
package com.intellij.openapi.actionSystem;

/**
 * Marker interface to ignore the visible children count and always call "actionPerformed".
 * In menus an ordinary menu item is shown for a group that sets {@link Presentation#isPerformGroup()} to true
 * and also has no visible children.
 * <p>
 * Note: not supported by {@link com.intellij.ide.ui.customization.CustomisedActionGroup}.
 *
 * @deprecated Use {@link com.intellij.openapi.actionSystem.impl.ActionMenu#SUPPRESS_SUBMENU} instead.
 */
@Deprecated(forRemoval = true)
public interface AlwaysPerformingActionGroup {

}
