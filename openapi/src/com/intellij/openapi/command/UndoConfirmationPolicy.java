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
package com.intellij.openapi.command;

import org.jetbrains.annotations.NonNls;

public class UndoConfirmationPolicy {
  private final String myName;

  private UndoConfirmationPolicy(@NonNls String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

  public final static UndoConfirmationPolicy REQUEST_CONFIRMATION = new UndoConfirmationPolicy("REQUEST_CONFIRMATION");
  public final static UndoConfirmationPolicy DO_NOT_REQUEST_CONFIRMATION = new UndoConfirmationPolicy("DO_NOT_REQUEST_CONFIRMATION");
  public final static UndoConfirmationPolicy DEFAULT = new UndoConfirmationPolicy("DEFAULT");
}
