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
package com.intellij.psi.stubsHierarchy.stubs;

import com.intellij.psi.stubsHierarchy.impl.ClassAnchor;
import com.intellij.psi.stubsHierarchy.impl.QualifiedName;

public final class ClassDeclaration extends Declaration {
  public final int mods;
  public final int myName;
  public final ClassAnchor.StubClassAnchor myClassAnchor;
  public QualifiedName[] mySupers;

  public ClassDeclaration(ClassAnchor.StubClassAnchor classAnchor,
                          int mods,
                          int name,
                          QualifiedName[] supers,
                          Declaration[] declarations)
  {
    super(declarations);
    this.myClassAnchor = classAnchor;
    this.mods = mods;
    this.myName = name;
    this.mySupers = supers;
  }
}
