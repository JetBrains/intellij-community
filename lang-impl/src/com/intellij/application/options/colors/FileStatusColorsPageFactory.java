package com.intellij.application.options.colors;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;

import java.util.Collection;
import java.util.ArrayList;

class FileStatusColorsPageFactory implements ColorAndFontPanelFactory {
  public NewColorAndFontPanel createPanel(ColorAndFontOptions options) {
    return NewColorAndFontPanel.create(new PreviewPanel.Empty(), ColorAndFontOptions.FILE_STATUS_GROUP, options, collectFileTypes(), null);
  }

  public String getPanelDisplayName() {
    return ColorAndFontOptions.FILE_STATUS_GROUP;
  }

  private static Collection<String> collectFileTypes() {
    ArrayList<String> result = new ArrayList<String>();
    FileStatus[] statuses = FileStatusFactory.SERVICE.getInstance().getAllFileStatuses();

    for (FileStatus status : statuses) {
      result.add(status.getText());
    }
    return result;
  }
}
