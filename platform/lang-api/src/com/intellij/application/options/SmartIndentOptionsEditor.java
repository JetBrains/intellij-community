/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.intellij.psi.codeStyle.CodeStyleDefaults.DEFAULT_CONTINUATION_INDENT_SIZE;

/**
 * @author yole
 */
public class SmartIndentOptionsEditor extends IndentOptionsEditor {
  public static final String CONTINUATION_INDENT_LABEL = ApplicationBundle.message("editbox.indent.continuation.indent");
  private JCheckBox myCbSmartTabs;

  private ContinuationOption myContinuationOption;
  private ContinuationOption myDeclarationParameterIndentOption;
  private ContinuationOption myGenericTypeParameterIndentOption;
  private ContinuationOption myCallParameterIndentOption;
  private ContinuationOption myChainedCallIndentOption;
  private ContinuationOption myArrayElementIndentOption;

  private List<ContinuationOption> myContinuationOptions = new ArrayList<>();

  private JCheckBox myCbKeepIndentsOnEmptyLines;

  public SmartIndentOptionsEditor() {
    myContinuationOption = createContinuationOption(
      CONTINUATION_INDENT_LABEL,
      options -> options.CONTINUATION_INDENT_SIZE,  (options, value) -> options.CONTINUATION_INDENT_SIZE = value,
      DEFAULT_CONTINUATION_INDENT_SIZE);
    myContinuationOption.setSupported(true);

    myDeclarationParameterIndentOption = createContinuationOption(
      "Declaration parameter indent:",
      options -> options.DECLARATION_PARAMETER_INDENT, (options, value) -> options.DECLARATION_PARAMETER_INDENT = value, -1);
    myGenericTypeParameterIndentOption = createContinuationOption(
      "Generic type parameter indent:",
      options -> options.GENERIC_TYPE_PARAMETER_INDENT, (options, value) -> options.GENERIC_TYPE_PARAMETER_INDENT = value, -1);
    myCallParameterIndentOption = createContinuationOption(
      "Call parameter indent:",
      options -> options.CALL_PARAMETER_INDENT, (options, value) -> options.CALL_PARAMETER_INDENT = value, -1
    );
    myChainedCallIndentOption = createContinuationOption(
      "Chained call indent:",
      options -> options.CHAINED_CALL_INDENT, (options, value) -> options.CHAINED_CALL_INDENT = value, -1
    );
    myArrayElementIndentOption = createContinuationOption(
      "Array element indent:",
      options -> options.ARRAY_ELEMENT_INDENT, (options, value) -> options.ARRAY_ELEMENT_INDENT = value, -1
    );
  }

  private ContinuationOption createContinuationOption(
    @NotNull String labelText,
    Function<CommonCodeStyleSettings.IndentOptions,Integer> getter,
    BiConsumer<CommonCodeStyleSettings.IndentOptions,Integer> setter,
    int defaultValue
  ) {
    ContinuationOption option = new ContinuationOption(labelText, getter, setter, defaultValue);
    myContinuationOptions.add(option);
    return option;
  }

  @Override
  protected void addTabOptions() {
    super.addTabOptions();

    myCbSmartTabs = new JCheckBox(ApplicationBundle.message("checkbox.indent.smart.tabs"));
    add(myCbSmartTabs, true);
  }

  @Override
  protected void addComponents() {
    super.addComponents();

    for (ContinuationOption option : myContinuationOptions) {
      option.addToEditor(this);
    }
    myContinuationOption.addListener(newValue -> updateDefaults(newValue));

    myCbKeepIndentsOnEmptyLines = new JCheckBox(ApplicationBundle.message("checkbox.indent.keep.indents.on.empty.lines"));
    add(myCbKeepIndentsOnEmptyLines);
  }

  private void updateDefaults(@NotNull Integer value) {
    for (ContinuationOption option : myContinuationOptions) {
      if (option != myContinuationOption) {
        option.setDefaultValueToDisplay(value);
      }
    }
  }

  @Override
  public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    boolean isModified = super.isModified(settings, options);
    isModified |= isFieldModified(myCbSmartTabs, options.SMART_TABS);
    for (ContinuationOption continuationOption : myContinuationOptions) {
      isModified |= continuationOption.isModified(options);
    }
    isModified |= isFieldModified(myCbKeepIndentsOnEmptyLines, options.KEEP_INDENTS_ON_EMPTY_LINES);
    return isModified;
  }

  @Override
  public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    super.apply(settings, options);
    for (ContinuationOption continuationOption : myContinuationOptions) {
      continuationOption.apply(options);
    }
    options.SMART_TABS = isSmartTabValid(options.INDENT_SIZE, options.TAB_SIZE) && myCbSmartTabs.isSelected();
    options.KEEP_INDENTS_ON_EMPTY_LINES = myCbKeepIndentsOnEmptyLines.isSelected();
  }

  @Override
  public void reset(@NotNull final CodeStyleSettings settings, @NotNull final CommonCodeStyleSettings.IndentOptions options) {
    super.reset(settings, options);
    for (ContinuationOption continuationOption : myContinuationOptions) {
      continuationOption.reset(options);
    }
    myCbSmartTabs.setSelected(options.SMART_TABS);
    myCbKeepIndentsOnEmptyLines.setSelected(options.KEEP_INDENTS_ON_EMPTY_LINES);
  }

  @Override
  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);

    boolean smartTabsChecked = enabled && myCbUseTab.isSelected();
    boolean smartTabsValid = smartTabsChecked && isSmartTabValid(getUIIndent(), getUITabSize());
    myCbSmartTabs.setEnabled(smartTabsValid);
    myCbSmartTabs.setToolTipText(
      smartTabsChecked && !smartTabsValid ? ApplicationBundle.message("tooltip.indent.must.be.multiple.of.tab.size.for.smart.tabs.to.operate") : null);

    myContinuationOption.setEnabled(enabled);
  }

  private static boolean isSmartTabValid(int indent, int tabSize) {
    return (indent / tabSize) * tabSize == indent;
  }

  @SuppressWarnings("unused") // reserved API
  public SmartIndentOptionsEditor withDeclarationParameterIndent() {
    myDeclarationParameterIndentOption.setSupported(true);
    return this;
  }

  @SuppressWarnings("unused") // reserved API
  public SmartIndentOptionsEditor  withGenericTypeParameterIndent() {
    myGenericTypeParameterIndentOption.setSupported(true);
    return this;
  }

  @SuppressWarnings("unused") // reserved API
  public SmartIndentOptionsEditor  withCallParameterIndent() {
    myCallParameterIndentOption.setSupported(true);
    return this;
  }

  @SuppressWarnings("unused") // reserved API
  public SmartIndentOptionsEditor  withChainedCallIndent() {
    myChainedCallIndentOption.setSupported(true);
    return this;
  }

  @SuppressWarnings("unused") // reserved API
  public SmartIndentOptionsEditor withArrayElementIndent() {
    myArrayElementIndentOption.setSupported(true);
    return this;
  }
}
