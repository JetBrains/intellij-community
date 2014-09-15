/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class EditorOptionsTopHitProvider extends OptionsTopHitProvider {
  public EditorOptionsTopHitProvider() {
    super("editor", createOptions());
  }

  private static Collection<BooleanOptionDescription> createOptions() {
    final List<BooleanOptionDescription> options = new ArrayList<BooleanOptionDescription>();
    options.add(editor("IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS", "Mouse", "checkbox.honor.camelhumps.words.settings.on.double.click",
                       "Editor.Behavior"));
    options.add(editor("IS_WHEEL_FONTCHANGE_ENABLED", "Mouse", SystemInfo.isMac ? "checkbox.enable.ctrl.mousewheel.changes.font.size.macos" : "checkbox.enable.ctrl.mousewheel.changes.font.size", "Editor.Behavior"));
    options.add(editor("IS_DND_ENABLED", "Mouse", "checkbox.enable.drag.n.drop.functionality.in.editor", "Editor.Behavior"));
    return options;
  }

  static EditorOptionDescription editor(String fieldName, String group, String property, String configurableId) {
    String name = "";
    if (!StringUtil.isEmpty(group)) {
      name += group + ": ";
    }
    name += StringUtil.stripHtml(ApplicationBundle.message(property), false);
    return new EditorOptionDescription(fieldName, name, configurableId);
  }
}
