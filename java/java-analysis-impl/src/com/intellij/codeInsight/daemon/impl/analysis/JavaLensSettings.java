// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;

import java.util.function.Consumer;

public class JavaLensSettings {
  public static final Topic<Consumer<JavaLensSettings>> JAVA_LENS_SETTINGS_CHANGED =
    new Topic<>("JAVA_LENS_SETTINGS_CHANGED", (Class<Consumer<JavaLensSettings>>)(Class<?>)Consumer.class);
  private boolean showUsages;
  private boolean showImplementations;

  public boolean isShowUsages() {
    return showUsages;
  }

  public void setShowUsages(boolean showUsages) {
    this.showUsages = showUsages;
    settingsChanged();
  }

  private void settingsChanged() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JAVA_LENS_SETTINGS_CHANGED).accept(this);
  }

  public boolean isShowImplementations() {
    return showImplementations;
  }

  public void setShowImplementations(boolean showImplementations) {
    this.showImplementations = showImplementations;
    settingsChanged();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JavaLensSettings settings = (JavaLensSettings)o;

    if (showUsages != settings.showUsages) return false;
    return showImplementations == settings.showImplementations;
  }

  @Override
  public int hashCode() {
    int result = showUsages ? 1 : 0;
    result = 31 * result + (showImplementations ? 1 : 0);
    return result;
  }
}
