/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public final class WatchItemData extends DescriptorData<WatchItemDescriptor>{
  private final TextWithImports myText;
  private final Value myValue;

  public WatchItemData(TextWithImports text, @Nullable Value value) {
    myText = text;
    myValue = value;
  }

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

  public DisplayKey<WatchItemDescriptor> getDisplayKey() {
    return new SimpleDisplayKey<>(myText.getText());
  }
}
