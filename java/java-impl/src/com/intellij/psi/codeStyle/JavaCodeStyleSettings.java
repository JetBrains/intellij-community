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
package com.intellij.psi.codeStyle;

public class JavaCodeStyleSettings extends CustomCodeStyleSettings {

  public JavaCodeStyleSettings(CodeStyleSettings container) {
    super("JavaCodeStyleSettings", container);
  }

  public boolean SPACES_WITHIN_ANGLE_BRACKETS = false;

  //Type arguments
  public boolean SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = false;

  //Type parameters
  public boolean SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = false;
  public boolean SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = true;

  public boolean DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION = false;
}
