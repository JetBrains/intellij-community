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
public class DuplicateAlternationBranchInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    quickfixTest("<warning descr=\"Duplicate branch in alternation\">\\t<caret></warning>|<warning descr=\"Duplicate branch in alternation\">\\x09</warning>", "\\x09", "Remove duplicate branch");
  }

  public void testMoreBranches() {
    quickfixTest("<warning descr=\"Duplicate branch in alternation\">a{3}</warning>|<warning descr=\"Duplicate branch in alternation\">a<caret><weak_warning descr=\"Fixed repetition range\">{3,3}</weak_warning></warning>|b|c", "a{3}|b|c", "Remove duplicate branch");
  }

  public void testOrderIrrelevant() {
    highlightTest("<warning descr=\"Duplicate branch in alternation\">[abc]</warning>|<warning descr=\"Duplicate branch in alternation\">[cba]</warning>");
  }

  public void testEmptyBranches() {
    highlightTest("|||");
  }

  public void testNoWarn() {
    highlightTest("([aeiou][^aeiou])*|([^aeiou][aeiou])*");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new DuplicateAlternationBranchInspection();
  }
}
