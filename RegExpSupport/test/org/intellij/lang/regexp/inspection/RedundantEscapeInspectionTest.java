/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RedundantEscapeInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    quickfixTest("<warning descr=\"Redundant character escape '\\;' in RegExp\">\\;</warning>", ";", "Remove redundant escape");
  }

  public void testCharacterClass() {
    highlightTest("<warning descr=\"Redundant character escape '\\-' in RegExp\">\\-</warning>[<warning descr=\"Redundant character escape '\\*' in RegExp\">\\*</warning>\\-\\[\\]\\\\<warning descr=\"Redundant character escape '\\+' in RegExp\">\\+</warning>]");
  }

  public void testWhitespace() {
    highlightTest("a<warning descr=\"Redundant character escape '\\ ' in RegExp\">\\ </warning>b<warning descr=\"Redundant character escape '\\ ' in RegExp\">\\ </warning>c");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new RedundantEscapeInspection();
  }
}
