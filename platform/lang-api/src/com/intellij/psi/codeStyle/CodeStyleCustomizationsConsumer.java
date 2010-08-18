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
public interface CodeStyleCustomizationsConsumer {
  void showAllStandardOptions();
  void showStandardOptions(String... optionNames);
  void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, String fieldName, String optionName, String groupName);

  String AROUND_OPERATORS = ApplicationBundle.message("group.spaces.around.operators");
  String BEFORE_PARENTHESES = ApplicationBundle.message("group.spaces.before.parentheses");
  String BEFORE_LEFT_BRACE = ApplicationBundle.message("group.spaces.before.left.brace");
  String WITHIN_PARENTHESES = ApplicationBundle.message("group.spaces.within");
  String TERNARY_OPERATOR = ApplicationBundle.message("group.spaces.in.ternary.operator");
  String TYPE_ARGUMENTS = ApplicationBundle.message("group.spaces.in.type.arguments");
  String OTHER = ApplicationBundle.message("group.spaces.other");
}
