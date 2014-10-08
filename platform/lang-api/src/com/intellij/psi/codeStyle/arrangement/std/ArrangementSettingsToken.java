/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.std;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for an atomic settings element. The general idea is to allow third-party plugin developers to use platform
 * codebase for managing arrangement settings, that's why we need a general purpose class that represent a setting.
 * I.e. third-party developers can create their own instances of this class and implement {@link ArrangementStandardSettingsAware}.
 * 
 * @author Denis Zhdanov
 * @since 3/6/13 12:38 PM
 */
public class ArrangementSettingsToken implements Comparable<ArrangementSettingsToken> {

  @NotNull protected String myId;
  @NotNull protected String myRepresentationName;

  public ArrangementSettingsToken(@NotNull String id, @NotNull String name) {
    myId = id;
    myRepresentationName = name;
  }

  @NotNull
  public String getId() {
    return myId;
  }
  
  @NotNull
  public String getRepresentationValue() {
    return myRepresentationName;
  }

  @Override
  public int compareTo(@NotNull ArrangementSettingsToken that) {
    return myId.compareTo(that.myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementSettingsToken that = (ArrangementSettingsToken)o;
    return myId.equals(that.myId);
  }

  @Override
  public String toString() {
    return myRepresentationName;
  }
}
