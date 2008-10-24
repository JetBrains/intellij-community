package com.intellij.application.options.colors;

import java.util.EventListener;

public interface ColorAndFontSettingsListener extends EventListener {
  void selectedOptionChanged(final Object selected);
  void schemeChanged();
  void settingsChanged();
  void selectionInPreviewChanged(final String typeToSelect);

  abstract class Abstract implements ColorAndFontSettingsListener {
    public void selectedOptionChanged(final Object selected) {

    }

    public void schemeChanged() {
    }

    public void settingsChanged() {
    }

    public void selectionInPreviewChanged(final String typeToSelect) {
    }
  }
}
