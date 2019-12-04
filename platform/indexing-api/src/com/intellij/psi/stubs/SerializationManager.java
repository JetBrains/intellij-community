// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.StubFileElementType;

import java.util.List;

public abstract class SerializationManager {
  public static SerializationManager getInstance() {
    return ApplicationManager.getApplication().getService(SerializationManager.class);
  }

  public void registerSerializer(ObjectStubSerializer serializer) {
    registerSerializer(serializer.getExternalId(), new Computable.PredefinedValueComputable<>(serializer));
  }

  protected abstract void registerSerializer(String externalId, Computable<ObjectStubSerializer> lazySerializer);

  protected abstract void initSerializers();

  public abstract String internString(String string);
}