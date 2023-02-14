// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public final class WatchItemData extends DescriptorData<WatchItemDescriptor> {
  private final TextWithImports myText;
  private final Value myValue;

  public WatchItemData(TextWithImports text, @Nullable Value value) {
    myText = text;
    myValue = value;
  }

  @Override
  protected WatchItemDescriptor createDescriptorImpl(@NotNull final Project project) {
    return myValue == null ? new WatchItemDescriptor(project, myText) : new WatchItemDescriptor(project, myText, myValue);
  }

  public boolean equals(final Object object) {
    if (object instanceof WatchItemData) {
      return myText.equals(((WatchItemData)object).myText);
    }
    return false;
  }

  public int hashCode() {
    return myText.hashCode();
  }

  @Override
  public DisplayKey<WatchItemDescriptor> getDisplayKey() {
    return new SimpleDisplayKey<>(myText.getText());
  }
}
