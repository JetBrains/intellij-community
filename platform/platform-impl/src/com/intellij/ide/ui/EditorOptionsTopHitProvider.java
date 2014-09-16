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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Konstantin Bulenkov
 */
public class EditorOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final Collection<BooleanOptionDescription> ourOptions = Collections.unmodifiableCollection(Arrays.asList(
    option("Mouse: " + messageApp("checkbox.honor.camelhumps.words.settings.on.double.click"), "IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS"),
    option("Mouse: " + messageApp(SystemInfo.isMac
                                  ? "checkbox.enable.ctrl.mousewheel.changes.font.size.macos"
                                  : "checkbox.enable.ctrl.mousewheel.changes.font.size"), "IS_WHEEL_FONTCHANGE_ENABLED"),
    option("Mouse: " + messageApp("checkbox.enable.drag.n.drop.functionality.in.editor"), "IS_DND_ENABLED"),
    option("Virtual Space: " + messageApp("checkbox.show.all.softwraps"), "IS_ALL_SOFTWRAPS_SHOWN"),
    option("Virtual Space: " + messageApp("checkbox.allow.placement.of.caret.after.end.of.line"), "IS_VIRTUAL_SPACE"),
    option("Virtual Space: " + messageApp("checkbox.allow.placement.of.caret.inside.tabs"), "IS_CARET_INSIDE_TABS"),
    option("Virtual Space: " + messageApp("checkbox.show.virtual.space.at.file.bottom"), "ADDITIONAL_PAGE_AT_BOTTOM")));

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(Project project) {
    return ourOptions;
  }

  @Override
  public String getId() {
    return "editor";
  }

  static BooleanOptionDescription option(String option, String field) {
    return new EditorOptionDescription(field, option, "preferences.editor");
  }
}
