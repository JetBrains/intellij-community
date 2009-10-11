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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class SerializationManager {
  public static SerializationManager getInstance() {
    return ApplicationManager.getApplication().getComponent(SerializationManager.class);
  }

  public abstract void registerSerializer(StubSerializer<? extends StubElement> serializer);

  public abstract void serialize(StubElement rootStub, OutputStream stream);

  public abstract StubElement deserialize(InputStream stream);

  public abstract StubSerializer getSerializer(StubElement rootStub);

  public abstract boolean isNameStorageCorrupted();

  public abstract void repairNameStorage();
}