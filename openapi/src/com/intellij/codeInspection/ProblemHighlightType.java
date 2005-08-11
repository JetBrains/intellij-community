/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 26, 2003
 * Time: 5:24:04 PM
 * To change this template use Options | File Templates.
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ProblemHighlightType {
  public static final ProblemHighlightType GENERIC_ERROR_OR_WARNING = new ProblemHighlightType("GENERIC_ERROR_OR_WARNING");
  public static final ProblemHighlightType LIKE_DEPRECATED = new ProblemHighlightType("LIKE_DEPRECATED");
  public static final ProblemHighlightType LIKE_UNKNOWN_SYMBOL = new ProblemHighlightType("LIKE_UNKNOWN_SYMBOL");
  public static final ProblemHighlightType LIKE_UNUSED_SYMBOL = new ProblemHighlightType("LIKE_UNUSED_SYMBOL");

  private final String myName; // for debug only

  private ProblemHighlightType(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }
}
