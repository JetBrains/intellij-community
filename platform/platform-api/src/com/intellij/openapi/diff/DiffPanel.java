/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.Disposable;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated
public interface DiffPanel extends DiffViewer, Disposable {
  void setTitle1(String title);
  void setTitle2(String title);
  void setContents(DiffContent content1, DiffContent content2);
  void setRequestFocus(boolean requestFocus);
  boolean hasDifferences();
  void setTooBigFileErrorContents();
  void setPatchAppliedApproximately();
  void removeStatusBar();
  void enableToolbar(final boolean value);
}
