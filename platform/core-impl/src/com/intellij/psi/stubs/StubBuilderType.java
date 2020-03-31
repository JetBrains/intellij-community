// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.tree.IStubFileElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class StubBuilderType {
  private static final Logger LOG = Logger.getInstance(StubBuilderType.class);
  private final IStubFileElementType myElementType;
  private final BinaryFileStubBuilder myBinaryFileStubBuilder;
  private final Object myBinarySubBuilder;

  StubBuilderType(@NotNull IStubFileElementType elementType) {
    myElementType = elementType;
    myBinaryFileStubBuilder = null;
    myBinarySubBuilder = null;
  }

  StubBuilderType(@NotNull BinaryFileStubBuilder binaryFileStubBuilder) {
    myElementType = null;
    myBinaryFileStubBuilder = binaryFileStubBuilder;
    myBinarySubBuilder = null;
  }

  StubBuilderType(@NotNull BinaryFileStubBuilder.CompositeBinaryFileStubBuilder binaryFileStubBuilder,
                  @Nullable Object binarySubBuilder) {
    myElementType = null;
    myBinaryFileStubBuilder = binaryFileStubBuilder;
    myBinarySubBuilder = binarySubBuilder;
  }

  BinaryFileStubBuilder getBinaryFileStubBuilder() {
    return myBinaryFileStubBuilder;
  }

  String getVersion() {
    if (myElementType != null) {
      if (myElementType.getLanguage() instanceof TemplateLanguage &&
          myElementType.getStubVersion() < IStubFileElementType.getTemplateStubVersion()) {
        PluginException.logPluginError(LOG, myElementType.getLanguage() + " stub version should call super.getStubVersion()",
                                       null, myElementType.getClass());
      }
      return myElementType.getClass().getName() + ":" + myElementType.getStubVersion();
    } else {
      assert myBinaryFileStubBuilder != null;
      String baseVersion = myBinaryFileStubBuilder.getClass().getName() + ":" + myBinaryFileStubBuilder.getStubVersion();
      if (myBinaryFileStubBuilder instanceof BinaryFileStubBuilder.CompositeBinaryFileStubBuilder) {
        return baseVersion + ":" + ((BinaryFileStubBuilder.CompositeBinaryFileStubBuilder)myBinaryFileStubBuilder).getSubBuilderVersion(myBinarySubBuilder);
      } else {
        return baseVersion;
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StubBuilderType type = (StubBuilderType)o;
    return Objects.equals(myElementType, type.myElementType) &&
           Objects.equals(myBinaryFileStubBuilder, type.myBinaryFileStubBuilder) &&
           Objects.equals(myBinarySubBuilder, type.myBinarySubBuilder);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myElementType, myBinaryFileStubBuilder, myBinarySubBuilder);
  }
}
