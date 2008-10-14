package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class EditorOptions extends SearchableConfigurable.Parent.Abstract {

  protected Configurable[] buildConfigurables() {
    final EditorOptionsProvider[] extensions = Extensions.getExtensions(EditorOptionsProvider.EP_NAME);
    Configurable[] result = new Configurable[extensions.length + 1];

    final EditorOptionsPanel behavior = new EditorOptionsPanel();
    result[0] = behavior.getConfigurable();

    for (int i = 0; i < extensions.length; i++) {
      EditorOptionsProvider each = extensions[i];
      result[i + 1] = each;
    }

    return result;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.editor");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public String getHelpTopic() {
    return "preferences.editor";
  }

  public String getId() {
    return getHelpTopic();
  }

}