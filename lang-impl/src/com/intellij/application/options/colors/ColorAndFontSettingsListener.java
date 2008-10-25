package com.intellij.application.options.colors;

import java.util.EventListener;

public interface ColorAndFontSettingsListener extends EventListener {
  void selectedOptionChanged(final Object selected);
  void schemeChanged(final Object source);
  void settingsChanged();
  void selectionInPreviewChanged(final String typeToSelect);

  void fontChanged();

  abstract class Abstract implements ColorAndFontSettingsListener {
    public void selectedOptionChanged(final Object selected) {

    }

    public void schemeChanged(final Object source) {
    }

    public void settingsChanged() {
    }

    public void selectionInPreviewChanged(final String typeToSelect) {
    }

    public void fontChanged() {
      
    }
  }
}
