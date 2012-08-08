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
package com.intellij.psi.codeStyle.arrangement.settings;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * // TODO den add doc 
 * 
 * @author Denis Zhdanov
 * @since 8/6/12 2:30 PM
 */
public class ArrangementSettings implements Cloneable {

  @NotNull private final Set<ArrangementModifier> myModifiers = EnumSet.noneOf(ArrangementModifier.class);
  
  @Nullable private ArrangementEntryType myType;

  @Nullable
  public ArrangementEntryType getType() {
    return myType;
  }

  public void setType(@Nullable ArrangementEntryType type) {
    myType = type;
  }

  @NotNull
  public Set<ArrangementModifier> getModifiers() {
    return myModifiers;
  }

  public boolean addModifier(@NotNull ArrangementModifier modifier) {
    return myModifiers.add(modifier);
  }

  public boolean removeModifier(@NotNull ArrangementModifier modifier) {
    return myModifiers.remove(modifier);
  }

  @Override
  protected ArrangementSettings clone() {
    ArrangementSettings result = new ArrangementSettings();
    result.setType(myType);
    result.myModifiers.addAll(myModifiers);
    return result;
  }
}
