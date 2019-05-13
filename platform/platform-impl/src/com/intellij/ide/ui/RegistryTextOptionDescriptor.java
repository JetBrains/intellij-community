/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.Changeable;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryTextOptionDescriptor extends OptionDescription implements Changeable {
  private final RegistryValue myValue;

  public RegistryTextOptionDescriptor(RegistryValue value) {
    super(value.getKey());
    myValue = value;
  }

  @Override
  public boolean hasChanged() {
    return myValue.isChangedFromDefault();
  }

  @Override
  public String getOption() {
    return myValue.getKey();
  }

  @Override
  public String getValue() {
    return myValue.asString();
  }

  @Override
  public boolean hasExternalEditor() {
    return true;
  }

  @Override
  public void invokeInternalEditor() {
    if (myValue.getKey().contains("color") && ColorUtil.fromHex(myValue.asString(), null) != null) {
      Color color = ColorChooser.chooseColor(IdeFrameImpl.getActiveFrame(), "Change Color For '" + myValue.getKey() + "'", ColorUtil.fromHex(myValue.asString()));
      if (color != null) {
        myValue.setValue(ColorUtil.toHex(color));
      }
    } else {
      String s = Messages.showInputDialog((Project)null, "Enter new value for '" + myValue.getKey() + "'", "Change Registry Value", null,
                                          myValue.asString(), new InputValidatorEx() {
          @Nullable
          @Override
          public String getErrorText(String inputString) {
            return canClose(inputString) ? null : "Should not be empty";
          }

          @Override
          public boolean checkInput(String inputString) {
            return canClose(inputString);
          }

          @Override
          public boolean canClose(String inputString) {
            return !StringUtil.isEmpty(inputString);
          }
        });
      if (s != null) {
        myValue.setValue(s);
      }
    }
  }
}
