/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.07.2006
 * Time: 13:51:49
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ui.ColoredTreeCellRenderer;

/**
 * Allows to modify the painting of changelists in the Changes view. Classes implementing this
 * interface need to be registered as project components.
 */
public interface ChangeListDecorator {
  void decorateChangeList(LocalChangeList changeList, ColoredTreeCellRenderer cellRenderer,
                          boolean selected, boolean expanded, boolean hasFocus);
}
