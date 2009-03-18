package com.intellij.application.options;

import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.FileTypes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class GeneralCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @NotNull
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, ApplicationBundle.message("title.general")) {
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new GeneralCodeStylePanel(settings);
      }

      public Icon getIcon() {
        return FileTypes.PLAIN_TEXT.getIcon();
      }

      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.general";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return ApplicationBundle.message("title.general");
  }
}
