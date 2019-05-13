/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Author: dmitrylomov
 */
public abstract class SerializationManagerEx extends SerializationManager {

  public static SerializationManagerEx getInstanceEx() {
    return (SerializationManagerEx) SerializationManager.getInstance();
  }

  public abstract void serialize(@NotNull Stub rootStub, @NotNull OutputStream stream);

  @NotNull
  public abstract Stub deserialize(@NotNull InputStream stream) throws SerializerNotFoundException;

  public abstract boolean isNameStorageCorrupted();

  public abstract void repairNameStorage();

  public abstract void flushNameStorage();

  public abstract void reinitializeNameStorage();
}
