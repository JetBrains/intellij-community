/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.StubFileElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class SerializationManager {

  protected final List<ObjectStubSerializer> myAllSerializers = new ArrayList<>();
  private volatile boolean mySerializersLoaded;

  public static SerializationManager getInstance() {
    return ApplicationManager.getApplication().getComponent(SerializationManager.class);
  }

  public void registerSerializer(ObjectStubSerializer serializer) {
    myAllSerializers.add(serializer);
  }

  protected void initSerializers() {
    if (mySerializersLoaded) return;
    synchronized (this) {
      if (mySerializersLoaded) return;
      for (StubElementTypeHolderEP holderEP : Extensions.getExtensions(StubElementTypeHolderEP.EP_NAME)) {
        holderEP.initialize();
      }
      final IElementType[] stubElementTypes = IElementType.enumerate(new IElementType.Predicate() {
        @Override
        public boolean matches(@NotNull final IElementType type) {
          return type instanceof StubSerializer;
        }
      });
      for (IElementType type : stubElementTypes) {
        if (type instanceof StubFileElementType &&
            StubFileElementType.DEFAULT_EXTERNAL_ID.equals(((StubFileElementType)type).getExternalId())) {
          continue;
        }
        StubSerializer stubSerializer = (StubSerializer)type;

        if (!myAllSerializers.contains(stubSerializer)) {
          registerSerializer(stubSerializer);
        }
      }
      mySerializersLoaded = true;
    }
  }

  public abstract String internString(String string);
}