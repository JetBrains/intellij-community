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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class EditorOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final Collection<BooleanOptionDescription> ourOptions = createOptions();

  public EditorOptionsTopHitProvider() {
    super("editor");
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(Project project) {
    return ourOptions;
  }

  private static Collection<BooleanOptionDescription> createOptions() {
    final List<BooleanOptionDescription> options = new ArrayList<BooleanOptionDescription>();
    options.add(editorMouse("IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS", "checkbox.honor.camelhumps.words.settings.on.double.click"));
    options.add(editorMouse("IS_WHEEL_FONTCHANGE_ENABLED", SystemInfo.isMac
                                                           ? "checkbox.enable.ctrl.mousewheel.changes.font.size.macos"
                                                           : "checkbox.enable.ctrl.mousewheel.changes.font.size"));
    options.add(editorMouse("IS_DND_ENABLED", "checkbox.enable.drag.n.drop.functionality.in.editor"));

    options.add(editorVirtualSpace("IS_ALL_SOFTWRAPS_SHOWN", "checkbox.show.all.softwraps"));
    options.add(editorVirtualSpace("IS_VIRTUAL_SPACE", "checkbox.allow.placement.of.caret.after.end.of.line"));
    options.add(editorVirtualSpace("IS_CARET_INSIDE_TABS", "checkbox.allow.placement.of.caret.inside.tabs"));
    options.add(editorVirtualSpace("ADDITIONAL_PAGE_AT_BOTTOM", "checkbox.show.virtual.space.at.file.bottom"));

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

  static EditorOptionDescription editorMouse(String fieldName, String property) {
    return editorBehavior(fieldName, "Mouse", property);
  }

  static EditorOptionDescription editorVirtualSpace(String fieldName, String property) {
    return editorBehavior(fieldName, "Virtual Space", property);
  }

  static EditorOptionDescription editorBehavior(String fieldName, String group, String property) {
    return editor(fieldName, group, property, "Editor.Behavior");
  }
}
