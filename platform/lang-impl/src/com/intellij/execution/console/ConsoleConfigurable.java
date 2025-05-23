// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable;
import com.intellij.openapi.options.ConfigurableBuilder;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.encoding.EncodingReference;
import com.intellij.ui.AddEditDeleteListPanel;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static com.intellij.openapi.options.Configurable.isCheckboxModified;
import static com.intellij.openapi.options.Configurable.isFieldModified;

@ApiStatus.Internal
public class ConsoleConfigurable extends BoundCompositeSearchableConfigurable<ConsoleOptionsProvider> {
  private static final Logger LOG = Logger.getInstance(ConsoleConfigurable.class);

  private ConsoleConfigurableUI ui;

  private MyAddDeleteListPanel myPositivePanel;
  private MyAddDeleteListPanel myNegativePanel;
  private final ConsoleFoldingSettings mySettings = ConsoleFoldingSettings.getSettings();

  public ConsoleConfigurable() {
    super(ExecutionBundle.message("configurable.ConsoleConfigurable.display.name"), "reference.idesettings.console.folding",
          ExecutionBundle.message("configurable.ConsoleConfigurable.display.name"));
  }

  @ApiStatus.Internal
  @Override
  @Unmodifiable
  @NotNull
  public List<ConsoleOptionsProvider> createConfigurables() {
    return ContainerUtil.sorted(ConfigurableWrapper.createConfigurables(ConsoleOptionsProviderEP.EP_NAME),
                                Comparator.comparing(ConfigurableBuilder::getConfigurableTitle));
  }

  @Override
  public @NotNull DialogPanel createPanel() {
    myPositivePanel =
      new MyAddDeleteListPanel(ApplicationBundle.message("console.fold.console.lines"),
                               ApplicationBundle.message("console.enter.substring.folded"));
    myNegativePanel = new MyAddDeleteListPanel(ApplicationBundle.message("console.fold.exceptions"),
                                               ApplicationBundle.message("console.enter.substring.dont.fold"));
    myPositivePanel.getEmptyText().setText(ApplicationBundle.message("console.fold.nothing"));
    myNegativePanel.getEmptyText().setText(ApplicationBundle.message("console.no.exceptions"));

    ui = new ConsoleConfigurableUI(editFoldingsOnly(), myPositivePanel, myNegativePanel, new Function1<>() {
      @Override
      public Unit invoke(Panel panel) {
        for (ConsoleOptionsProvider configurable : getConfigurables()) {
          appendDslConfigurable(panel, configurable);
        }
        return null;
      }
    });

    return ui.getContent();
  }

  protected boolean editFoldingsOnly() {
    return false;
  }

  public void addRule(@NotNull String rule) {
    myPositivePanel.addRule(rule);
  }

  @Override
  public boolean isModified() {
    boolean isModified = super.isModified();

    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    isModified |= !ContainerUtil.newHashSet(myNegativePanel.getListItems()).equals(new HashSet<>(mySettings.getNegativePatterns()));
    isModified |= !ContainerUtil.newHashSet(myPositivePanel.getListItems()).equals(new HashSet<>(mySettings.getPositivePatterns()));
    isModified |= isCheckboxModified(ui.cbUseSoftWrapsAtConsole, editorSettings.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE));
    UISettings uiSettings = UISettings.getInstance();
    isModified |= isFieldModified(ui.commandsHistoryLimitField, uiSettings.getConsoleCommandHistoryLimit());
    if (ConsoleBuffer.useCycleBuffer()) {
      isModified |= isCheckboxModified(ui.cbOverrideConsoleCycleBufferSize, uiSettings.getOverrideConsoleCycleBufferSize());
      isModified |= isFieldModified(ui.consoleCycleBufferSizeField, uiSettings.getConsoleCycleBufferSizeKb());
    }
    isModified |= isEncodingModified();

    return isModified;
  }

  private boolean isEncodingModified() {
    EncodingManager encodingManager = EncodingManager.getInstance();

    EncodingReference defaultEncoding = EncodingReference.DEFAULT;
    if (encodingManager instanceof EncodingManagerImpl) {
      defaultEncoding = ((EncodingManagerImpl)encodingManager).getDefaultConsoleEncodingReference();
    }

    EncodingReference consoleEncoding = ui.encodingComboBox.getSelectedEncodingReference();
    return !defaultEncoding.equals(consoleEncoding);
  }

  @Override
  public void apply() {
    super.apply();

    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    UISettings settingsManager = UISettings.getInstance();
    UISettingsState uiSettings = settingsManager.getState();
    EncodingManager encodingManager = EncodingManager.getInstance();

    editorSettings.setUseSoftWraps(ui.cbUseSoftWrapsAtConsole.isSelected(), SoftWrapAppliancePlaces.CONSOLE);
    boolean uiSettingsChanged = false;
    if (isFieldModified(ui.commandsHistoryLimitField, uiSettings.getConsoleCommandHistoryLimit())) {
      uiSettings.setConsoleCommandHistoryLimit(
        Math.max(0, Math.min(1000, Integer.parseInt(ui.commandsHistoryLimitField.getText().trim()))));
      uiSettingsChanged = true;
    }
    if (ConsoleBuffer.useCycleBuffer()) {
      if (isCheckboxModified(ui.cbOverrideConsoleCycleBufferSize, uiSettings.getOverrideConsoleCycleBufferSize())) {
        uiSettings.setOverrideConsoleCycleBufferSize(ui.cbOverrideConsoleCycleBufferSize.isSelected());
        uiSettingsChanged = true;
      }
      if (isFieldModified(ui.consoleCycleBufferSizeField, uiSettings.getConsoleCycleBufferSizeKb())) {
        uiSettings.setConsoleCycleBufferSizeKb(Math.max(0, Integer.parseInt(ui.consoleCycleBufferSizeField.getText().trim())));
        uiSettingsChanged = true;
      }
    }
    if (uiSettingsChanged) {
      settingsManager.fireUISettingsChanged();
    }
    if (isEncodingModified()) {
      if (encodingManager instanceof EncodingManagerImpl) {
        ((EncodingManagerImpl)encodingManager).setDefaultConsoleEncodingReference(ui.encodingComboBox.getSelectedEncodingReference());
      }
    }

    myNegativePanel.applyTo(mySettings.getNegativePatterns());
    myPositivePanel.applyTo(mySettings.getPositivePatterns());
  }

  @Override
  public void reset() {
    super.reset();

    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    UISettingsState uiSettings = UISettings.getInstance().getState();
    EncodingManager encodingManager = EncodingManager.getInstance();

    ui.cbUseSoftWrapsAtConsole.setSelected(editorSettings.isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE));
    ui.commandsHistoryLimitField.setText(Integer.toString(uiSettings.getConsoleCommandHistoryLimit()));

    ui.cbOverrideConsoleCycleBufferSize.setEnabled(ConsoleBuffer.useCycleBuffer());
    ui.cbOverrideConsoleCycleBufferSize.setSelected(uiSettings.getOverrideConsoleCycleBufferSize());
    ui.consoleCycleBufferSizeField.setEnabled(ConsoleBuffer.useCycleBuffer() && uiSettings.getOverrideConsoleCycleBufferSize());
    ui.consoleCycleBufferSizeField.setText(Integer.toString(uiSettings.getConsoleCycleBufferSizeKb()));

    EncodingReference encodingReference = EncodingReference.DEFAULT;
    if (encodingManager instanceof EncodingManagerImpl) {
      encodingReference = ((EncodingManagerImpl)encodingManager).getDefaultConsoleEncodingReference();
    }
    else {
      LOG.warn("Expected EncodingManagerImpl but got " + encodingManager.getClass().getName());
    }
    ui.encodingComboBox.reset(encodingReference);

    myNegativePanel.resetFrom(mySettings.getNegativePatterns());
    myPositivePanel.resetFrom(mySettings.getPositivePatterns());
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();

    ui = null;
    myNegativePanel = null;
    myPositivePanel = null;
  }

  private static final class MyAddDeleteListPanel extends AddEditDeleteListPanel<String> {
    private final @NlsContexts.DialogMessage String myQuery;

    MyAddDeleteListPanel(@NlsContexts.Label String title, @NlsContexts.DialogMessage String query) {
      super(title, new ArrayList<>());
      myQuery = query;
      ListSpeedSearch.installOn(myList);
    }

    @Override
    protected @Nullable String findItemToAdd() {
      return showEditDialog("");
    }

    private @Nullable String showEditDialog(final String initialValue) {
      return Messages.showInputDialog(this, myQuery, ExecutionBundle.message("dialog.title.folding.pattern"), Messages.getQuestionIcon(),
                                      initialValue, new InputValidatorEx() {
          @Override
          public boolean checkInput(String inputString) {
            return !StringUtil.isEmpty(inputString);
          }

          @Override
          public boolean canClose(String inputString) {
            return !StringUtil.isEmpty(inputString);
          }

          @Override
          public @NlsContexts.DetailedDescription @Nullable String getErrorText(String inputString) {
            if (!checkInput(inputString)) {
              return ExecutionBundle.message("message.console.folding.rule.string.cannot.be.empty");
            }
            return null;
          }
        });
    }

    void resetFrom(List<String> patterns) {
      myListModel.clear();
      patterns.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(myListModel::addElement);
    }

    void applyTo(List<? super String> patterns) {
      patterns.clear();
      for (Object o : getListItems()) {
        patterns.add((String)o);
      }
    }

    public void addRule(String rule) {
      addElement(rule);
    }

    @Override
    protected String editSelectedItem(String item) {
      return showEditDialog(item);
    }
  }
}
