/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.usages.impl;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class NullUsage implements Usage {
  @NotNull
  public UsagePresentation getPresentation() {
    throw new IllegalAccessError();
  }

  public boolean isValid() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  public FileEditorLocation getLocation() {
    return null;
  }

  public void selectInEditor() {

  }

  public void highlightInEditor() {

  }

  public void navigate(final boolean requestFocus) {

  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}
