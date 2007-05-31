/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: May 30, 2007
 */
public final class WatchItemData extends DescriptorData<WatchItemDescriptor>{
  private final TextWithImports myText;
  private final Value myValue;

  public WatchItemData(TextWithImports text, @Nullable Value value) {
    myText = text;
    myValue = value;
  }

  protected WatchItemDescriptor createDescriptorImpl(final Project project) {
    return new WatchItemDescriptor(project, myText, myValue);
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

  public DisplayKey<WatchItemDescriptor> getDisplayKey() {
    return new SimpleDisplayKey<WatchItemDescriptor>(myText.getText());
  }
}
