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
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public enum LanguageLevel {
  JDK_1_3("1.3"), JDK_1_4("1.4"), JDK_1_5("1.5"), JDK_1_6("1.6"), JDK_1_7("1.7"), JDK_1_8("1.8"), JDK_1_9("1.9"), JDK_X("10");

  private final String myComplianceOption;

  LanguageLevel(String complianceOption) {
    myComplianceOption = complianceOption;
  }

  /**
   * String representation of the level, suitable to pass as a value of compiler's "-source" and "-target" options.
   * Should work for Javac and ECJ (including ECJ-based) compilers.
   */
  @NotNull
  public String getComplianceOption() {
    return myComplianceOption;
  }
}
