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

import com.intellij.openapi.components.*;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@State(
  name = "FileBasedIndex",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(
      file = "$APP_CONFIG$/stubIndex.xml")
  }
)
public class StubIndexComponent extends StubIndexImpl implements ApplicationComponent, PersistentStateComponent<StubIndexState> {
  public StubIndexComponent(FileBasedIndex fileBasedIndex) throws IOException {
    super(fileBasedIndex);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    // This index must be disposed only after StubUpdatingIndex is disposed
    // To ensure this, disposing is done explicitly from StubUpdatingIndex by calling dispose() method
    // do not call this method here to avoid double-disposal
  }
}
