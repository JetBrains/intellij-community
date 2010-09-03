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
  String SPACES_BEFORE_KEYWORD = ApplicationBundle.message("group.spaces.after.right.brace");
  String SPACES_WITHIN = ApplicationBundle.message("group.spaces.within");
  String SPACES_IN_TERNARY_OPERATOR = ApplicationBundle.message("group.spaces.in.ternary.operator");
  String SPACES_WITHIN_TYPE_ARGUMENTS = ApplicationBundle.message("group.spaces.in.type.arguments");
  String SPACES_OTHER = ApplicationBundle.message("group.spaces.other");

  String BLANK_LINES = ApplicationBundle.message("title.blank.lines");
  String BLANK_LINES_KEEP = ApplicationBundle.message("title.keep.blank.lines");

  String WRAPPING_KEEP = ApplicationBundle.message("wrapping.keep.when.reformatting");
  String WRAPPING_BRACES = ApplicationBundle.message("wrapping.brace.placement");
  String WRAPPING_METHOD_PARAMETERS = ApplicationBundle.message("wrapping.method.parameters");
  String WRAPPING_METHOD_PARENTHESES = ApplicationBundle.message("wrapping.method.parentheses");
  String WRAPPING_METHOD_ARGUMENTS_WRAPPING = ApplicationBundle.message("wrapping.method.arguments");
  String WRAPPING_CALL_CHAIN = ApplicationBundle.message("wrapping.chained.method.calls");
  String WRAPPING_IF_STATEMENT = ApplicationBundle.message("wrapping.if.statement");
  String WRAPPING_FOR_STATEMENT = ApplicationBundle.message("wrapping.for.statement");
  String WRAPPING_WHILE_STATEMENT = ApplicationBundle.message("wrapping.while.statement");
  String WRAPPING_DOWHILE_STATEMENT = ApplicationBundle.message("wrapping.dowhile.statement");
  String WRAPPING_SWITCH_STATEMENT = ApplicationBundle.message("wrapping.switch.statement");
  String WRAPPING_TRY_STATEMENT = ApplicationBundle.message("wrapping.try.statement");
  String WRAPPING_BINARY_OPERATION = ApplicationBundle.message("wrapping.binary.operations");
  String WRAPPING_EXTENDS_LIST = ApplicationBundle.message("wrapping.extends.implements.list");
  String WRAPPING_EXTENDS_KEYWORD = ApplicationBundle.message("wrapping.extends.implements.keyword");
  String WRAPPING_THROWS_LIST = ApplicationBundle.message("wrapping.throws.list");
  String WRAPPING_THROWS_KEYWORD = ApplicationBundle.message("wrapping.throws.keyword");
  String WRAPPING_TERNARY_OPERATION = ApplicationBundle.message("wrapping.ternary.operation");
  String WRAPPING_ASSIGNMENT = ApplicationBundle.message("wrapping.assignment.statement");
  String WRAPPING_FIELDS_VARIABLES_GROUPS = ApplicationBundle.message("wrapping.assignment.variables.groups");
  String WRAPPING_ARRAY_INITIALIZER = ApplicationBundle.message("wrapping.array.initializer");
  String WRAPPING_MODIFIER_LIST = ApplicationBundle.message("wrapping.modifier.list");
  String WRAPPING_ASSERT_STATEMENT = ApplicationBundle.message("wrapping.assert.statement");

  void showAllStandardOptions();

  void showStandardOptions(String... optionNames);

  void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, String fieldName, String title, String groupName);

  void renameStandardOption(String fieldName, String newTitle);
}
