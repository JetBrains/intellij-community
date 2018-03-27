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
@SuppressWarnings("RegExpRepeatedSpace")
public class RepeatedSpaceInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    highlightTest("<warning descr=\"2 consecutive spaces in RegExp\">  </warning>");
  }

  public void testIgnoreQuoted() {
    highlightTest("\\Q     \\E");
  }

  public void testIgnoreInClass() {
    highlightTest("[ <warning descr=\"Duplicate character ' ' inside character class\"> </warning><warning descr=\"Duplicate character ' ' inside character class\"> </warning>]");
  }

  public void testReplacement() {
    quickfixTest("<warning descr=\"5 consecutive spaces in RegExp\">     </warning>", " {5}", "Replace with ' {5}'");
  }

  @Override
  @NotNull
  protected LocalInspectionTool getInspection() {
    return new RepeatedSpaceInspection();
  }
}
