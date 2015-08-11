/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.ui;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.Pair;
import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.*;

import java.util.*;

/**
 * @author Roman.Shein
 * @since 03.08.2015.
 *
 *<p>
 * This is a temporary stub. Should actually modify providers from IDEA core to allow extraction of field names and
 * associated values.
 */
public class FCodeStyleBlankLinesProvider {

  private final Map<Pair<String, String>, String> groupAndFieldToName;
  private final Map<String, List<String>> groupToFields;

  public FCodeStyleBlankLinesProvider() {
    groupAndFieldToName = new LinkedHashMap<Pair<String, String>, String>();
    groupToFields = new LinkedHashMap<String, List<String>>();

    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.before.package.statement"), "BLANK_LINES_BEFORE_PACKAGE");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.after.package.statement"), "BLANK_LINES_AFTER_PACKAGE");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.before.imports"), "BLANK_LINES_BEFORE_IMPORTS");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.after.imports"), "BLANK_LINES_AFTER_IMPORTS");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.around.class"), "BLANK_LINES_AROUND_CLASS");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.after.class.header"), "BLANK_LINES_AFTER_CLASS_HEADER");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.after.anonymous.class.header"),
        "BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER");
    createOption(BLANK_LINES, "Around field in interface:", "BLANK_LINES_AROUND_FIELD_IN_INTERFACE");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.around.field"), "BLANK_LINES_AROUND_FIELD");
    createOption(BLANK_LINES, "Around method in interface:", "BLANK_LINES_AROUND_METHOD_IN_INTERFACE");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.around.method"), "BLANK_LINES_AROUND_METHOD");
    createOption(BLANK_LINES, ApplicationBundle.message("editbox.blanklines.before.method.body"), "BLANK_LINES_BEFORE_METHOD_BODY");

    createOption(BLANK_LINES_KEEP, ApplicationBundle.message("editbox.keep.blanklines.in.declarations"), "KEEP_BLANK_LINES_IN_DECLARATIONS");
    createOption(BLANK_LINES_KEEP, ApplicationBundle.message("editbox.keep.blanklines.in.code"), "KEEP_BLANK_LINES_IN_CODE");
    createOption(BLANK_LINES_KEEP, ApplicationBundle.message("editbox.keep.blanklines.before.rbrace"), "KEEP_BLANK_LINES_BEFORE_RBRACE");
  }

  private void createOption(String groupName, String title, String fieldName) {
    FCodeStyleSettingsNameProvider.addToGroupsMap(groupAndFieldToName, groupToFields, fieldName, title, groupName);
  }

  public Map<Pair<String, String>, String> getGroupAndFieldToName() {
    return groupAndFieldToName;
  }

  public Map<String, List<String>> getGroupToFields() {
    return groupToFields;
  }
}
