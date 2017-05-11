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
public class EmptyAlternationBranchInspectionTest extends RegExpInspectionTestCase {

  public void testVeryEmpty() {
    quickfixTest("<warning descr=\"Empty branch in alternation\">|</warning>", "", "Remove empty branch");
  }

  public void testEmptyLeft() {
    quickfixTest("<warning descr=\"Empty branch in alternation\">|</warning>right", "right", "Remove empty branch");
  }

  public void testEmptyRight() {
    quickfixTest("left<warning descr=\"Empty branch in alternation\">|<caret></warning>", "left", "Remove empty branch");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new EmptyAlternationBranchInspection();
  }
}
