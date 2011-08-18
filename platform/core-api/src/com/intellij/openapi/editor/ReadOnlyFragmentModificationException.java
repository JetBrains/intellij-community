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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NonNls;

public class ReadOnlyFragmentModificationException extends RuntimeException {
  private final DocumentEvent myIllegalAttemptEvent;
  private final RangeMarker myGuardedBlock;
  @NonNls public static final String MESSAGE = "Attempt to modify read-only fragment";

  public ReadOnlyFragmentModificationException(DocumentEvent illegalAttemptEvent, RangeMarker guardedBlock) {
    super(MESSAGE);
    myIllegalAttemptEvent = illegalAttemptEvent;
    myGuardedBlock = guardedBlock;
  }

  public RangeMarker getGuardedBlock() {
    return myGuardedBlock;
  }

  public DocumentEvent getIllegalAttemptEvent() {
    return myIllegalAttemptEvent;
  }
}
