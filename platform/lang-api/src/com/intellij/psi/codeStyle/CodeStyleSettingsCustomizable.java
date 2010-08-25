/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;

/**
 * @author yole
 */
public interface CodeStyleSettingsCustomizable {
  String SPACES_AROUND_OPERATORS = ApplicationBundle.message("group.spaces.around.operators");
  String SPACES_BEFORE_PARENTHESES = ApplicationBundle.message("group.spaces.before.parentheses");
  String SPACES_BEFORE_LEFT_BRACE = ApplicationBundle.message("group.spaces.before.left.brace");
  String SPACES_WITHIN = ApplicationBundle.message("group.spaces.within");
  String SPACES_IN_TERNARY_OPERATOR = ApplicationBundle.message("group.spaces.in.ternary.operator");
  String SPACES_WITHIN_TYPE_ARGUMENTS = ApplicationBundle.message("group.spaces.in.type.arguments");
  String SPACES_OTHER = ApplicationBundle.message("group.spaces.other");

  String BLANK_LINES = ApplicationBundle.message("title.blank.lines");
  String BLANK_LINES_KEEP = ApplicationBundle.message("title.keep.blank.lines");

  void showAllStandardOptions();

  void showStandardOptions(String... optionNames);

  void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, String fieldName, String title, String groupName);

  void renameStandardOption(String fieldName, String newTitle);
}
