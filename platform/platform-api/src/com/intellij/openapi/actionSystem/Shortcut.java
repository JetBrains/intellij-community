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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.keymap.KeymapUtil;

/**
 * A keyboard or mouse shortcut which can be used for invoking an action.
 *
 * @see ShortcutSet
 */
public abstract class Shortcut {
  public static final Shortcut[] EMPTY_ARRAY = new Shortcut[0];
  Shortcut(){
  }

  public abstract boolean isKeyboard();

  public abstract boolean startsWith(final Shortcut sc);

  @Override
  public String toString() {
    return KeymapUtil.getShortcutText(this);
  }
}
