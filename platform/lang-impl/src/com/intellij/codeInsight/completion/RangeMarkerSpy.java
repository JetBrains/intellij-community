/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;

/**
 * @author peter
 */
public abstract class RangeMarkerSpy implements DocumentListener {
  private final RangeMarker myMarker;

  public RangeMarkerSpy(RangeMarker marker) {
    myMarker = marker;
    assert myMarker.isValid();
  }

  protected abstract void invalidated(DocumentEvent e);

  @Override
  public void documentChanged(DocumentEvent e) {
    if (!myMarker.isValid()) {
      invalidated(e);
    }
  }
}
