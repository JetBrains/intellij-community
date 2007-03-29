package com.intellij.localvcs.integration;

import com.intellij.openapi.components.SettingsSavingComponent;

public interface ILocalVcsComponent extends SettingsSavingComponent {
  ILocalVcsAction startAction(String name);

  boolean isEnabled();
}
