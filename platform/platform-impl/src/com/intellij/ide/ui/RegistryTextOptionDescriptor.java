// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.Changeable;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("HardCodedStringLiteral")
public final class RegistryTextOptionDescriptor extends OptionDescription implements Changeable {
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
      Color color = ColorChooserService.getInstance().showDialog(IdeFrameImpl.getActiveFrame(), "Change Color For '" + myValue.getKey() + "'", ColorUtil.fromHex(myValue.asString()));
      if (color != null) {
        myValue.setValue(ColorUtil.toHex(color));
      }
    } else {
      String s = Messages.showInputDialog((Project)null, "Enter new value for '" + myValue.getKey() + "'", "Change Registry Value", null,
                                          myValue.asString(), new InputValidatorEx() {
          @Override
          public @Nullable String getErrorText(String inputString) {
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
