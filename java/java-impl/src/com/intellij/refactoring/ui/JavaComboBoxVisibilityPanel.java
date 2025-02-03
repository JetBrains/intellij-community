/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.ui;

import com.intellij.psi.util.AccessModifier;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaComboBoxVisibilityPanel extends ComboBoxVisibilityPanel<String> {
  public JavaComboBoxVisibilityPanel(List<AccessModifier> modifiers) {
    super(ContainerUtil.map2Array(modifiers, String.class, AccessModifier::toPsiModifier), 
          ContainerUtil.map2Array(modifiers, String.class, AccessModifier::toString));
  }

  public JavaComboBoxVisibilityPanel() {
    this(AccessModifier.ALL_MODIFIERS);
  }
}
