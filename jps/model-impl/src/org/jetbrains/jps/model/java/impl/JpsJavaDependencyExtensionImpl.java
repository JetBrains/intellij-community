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
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

class JpsJavaDependencyExtensionImpl extends JpsElementBase<JpsJavaDependencyExtensionImpl> implements JpsJavaDependencyExtension {
  private boolean myExported;
  private JpsJavaDependencyScope myScope;

  JpsJavaDependencyExtensionImpl(boolean exported,
                                        JpsJavaDependencyScope scope) {
    myExported = exported;
    myScope = scope;
  }

  JpsJavaDependencyExtensionImpl(JpsJavaDependencyExtensionImpl original) {
    myExported = original.myExported;
    myScope = original.myScope;
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean exported) {
    if (myExported != exported) {
      myExported = exported;
    }
  }

  @NotNull
  @Override
  public JpsJavaDependencyScope getScope() {
    return myScope;
  }

  @Override
  public void setScope(@NotNull JpsJavaDependencyScope scope) {
    if (!scope.equals(myScope)) {
      myScope = scope;
    }
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl createCopy() {
    return new JpsJavaDependencyExtensionImpl(this);
  }
}
