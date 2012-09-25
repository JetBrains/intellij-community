/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/19/12 6:41 PM
 */
public class JavaArrangementMethodDependencyInfo {

  @NotNull private final List<JavaElementArrangementEntry> getDependentMethods = new ArrayList<JavaElementArrangementEntry>();
  @NotNull private final JavaElementArrangementEntry myAnchorMethod;

  public JavaArrangementMethodDependencyInfo(@NotNull JavaElementArrangementEntry method) {
    myAnchorMethod = method;
  }

  @NotNull
  public List<JavaElementArrangementEntry> getGetDependentMethods() {
    return getDependentMethods;
  }

  @NotNull
  public JavaElementArrangementEntry getAnchorMethod() {
    return myAnchorMethod;
  }
}
