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

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/18/12 3:33 PM
 */
public class JavaArrangementPropertyInfo {

  @Nullable private JavaElementArrangementEntry myGetter;
  private final List<JavaElementArrangementEntry> mySetters = new SmartList<>();

  @Nullable
  public JavaElementArrangementEntry getGetter() {
    return myGetter;
  }

  public void setGetter(@Nullable JavaElementArrangementEntry getter) {
    myGetter = getter;
  }


  public void addSetter(@NotNull JavaElementArrangementEntry setter) {
    mySetters.add(setter);
  }

  /**
   * @return list of setter entries, that always ordered by signature (not depends on position order)
   */
  public List<JavaElementArrangementEntry> getSetters() {
    return mySetters;
  }
}
