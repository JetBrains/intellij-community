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

import org.jetbrains.annotations.ApiStatus;

/**
 * Markup interface to ignore the visible children count and always call "actionPerformed".
 * In menus {@link Presentation#isPerformGroup()} and {@link ActionGroup#canBePerformed(DataContext)}
 * is combined with visible children count to decide whether to show "... >" submenu or an ordinary menu item.
 *
 * Note: not supported by {@link com.intellij.ide.ui.customization.CustomisedActionGroup}.
 *
 * @author gregsh
 *
 * @deprecated Use {@link com.intellij.openapi.actionSystem.impl.ActionMenu#SUPPRESS_SUBMENU} instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
public interface AlwaysPerformingActionGroup {

}
