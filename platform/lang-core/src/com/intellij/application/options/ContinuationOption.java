// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.ui.components.fields.valueEditors.ValueEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ContinuationOption implements CodeStyleConstraints {
  private @Nullable IntegerField myField;
  private boolean mySupported;
  private final @NlsContexts.Label String myName;
  private final Function<? super CommonCodeStyleSettings.IndentOptions, Integer> myGetter;
  private final BiConsumer<? super CommonCodeStyleSettings.IndentOptions, ? super Integer> mySetter;
  private final int myDefaultValue;
  private JLabel myLabel;

  public ContinuationOption(@NlsContexts.Label String name,
                            Function<? super CommonCodeStyleSettings.IndentOptions, Integer> getter,
                            BiConsumer<? super CommonCodeStyleSettings.IndentOptions, ? super Integer> setter,
                            int defaultValue) {
    myName = name;
    myGetter = getter;
    mySetter = setter;
    myDefaultValue = defaultValue;
  }

  public void addToEditor(@NotNull IndentOptionsEditor editor) {
    if (mySupported) {
      myLabel = new JLabel(myName);
      myField = editor.createIndentTextField("Continuation indent", MIN_INDENT_SIZE, MAX_INDENT_SIZE, myDefaultValue);
      editor.add(myLabel, myField);
    }
  }


  public void setSupported(boolean supported) {
    mySupported = supported;
  }

  public void setEnabled(boolean isEnabled) {
    if (mySupported && myField != null && myLabel != null) {
      myField.setEnabled(isEnabled);
      myLabel.setEnabled(isEnabled);
    }
  }

  public boolean isModified(@NotNull CommonCodeStyleSettings.IndentOptions options) {
    return mySupported && myField != null && !myField.getValue().equals(myGetter.apply(options));
  }

  public void reset(@NotNull CommonCodeStyleSettings.IndentOptions options) {
    if (mySupported && myField != null) {
      myField.setValue(myGetter.apply(options));
      setDefaultValueToDisplay(options.CONTINUATION_INDENT_SIZE);
    }
  }

  public void apply(@NotNull CommonCodeStyleSettings.IndentOptions options) {
    if (mySupported && myField != null) {
      mySetter.accept(options, myField.getValue());
    }
  }

  public void addListener(@NotNull ValueEditor.Listener<Integer> listener) {
    assert myField != null;
    myField.getValueEditor().addListener(listener);
  }

  public void setDefaultValueToDisplay(int value) {
    if (mySupported && myField != null) {
      myField.setDefaultValueText(Integer.toString(value));
    }
  }

  public void setVisible(boolean visible) {
    if (myField != null && myLabel != null) {
      myLabel.setVisible(visible);
      myField.setVisible(visible);
    }
  }
}
