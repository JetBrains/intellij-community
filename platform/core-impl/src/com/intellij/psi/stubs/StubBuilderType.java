// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.StubFileElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class StubBuilderType {
  private static final Logger LOG = Logger.getInstance(StubBuilderType.class);
  private final IStubFileElementType myElementType;
  private final List<String> myProperties;
  private final BinaryFileStubBuilder myBinaryFileStubBuilder;
  private final Object myBinarySubBuilder;

  StubBuilderType(@NotNull IStubFileElementType elementType, @NotNull List<String> properties) {
    myElementType = elementType;
    myProperties = properties;
    myBinaryFileStubBuilder = null;
    myBinarySubBuilder = null;
  }

  StubBuilderType(@NotNull BinaryFileStubBuilder binaryFileStubBuilder) {
    myElementType = null;
    myBinaryFileStubBuilder = binaryFileStubBuilder;
    myBinarySubBuilder = null;
    myProperties = Collections.emptyList();
  }

  StubBuilderType(@NotNull BinaryFileStubBuilder.CompositeBinaryFileStubBuilder binaryFileStubBuilder,
                  @Nullable Object binarySubBuilder) {
    myElementType = null;
    myBinaryFileStubBuilder = binaryFileStubBuilder;
    myBinarySubBuilder = binarySubBuilder;
    myProperties = Collections.emptyList();
  }

  @NotNull Class<?> getClassToBlameInCaseOfException() {
    if (myElementType != null) {
      return myElementType.getClass();
    }
    if (myBinarySubBuilder == null) {
      return myBinaryFileStubBuilder.getClass();
    }
    return myBinarySubBuilder.getClass();
  }

  BinaryFileStubBuilder getBinaryFileStubBuilder() {
    return myBinaryFileStubBuilder;
  }

  public IStubFileElementType getStubFileElementType() {
    return myElementType;
  }

  public String getVersion() {
    if (myElementType != null) {
      int elementTypeStubVersion = myElementType.getStubVersion();

      if (myElementType.getLanguage() instanceof TemplateLanguage) {
        int templateStubBaseVersion = IStubFileElementType.getTemplateStubBaseVersion();
        if (elementTypeStubVersion < templateStubBaseVersion) {
          PluginException.logPluginError(LOG, myElementType.getClass() + " " +
                                              myElementType.getLanguage() +
                                              " version=" + elementTypeStubVersion + " " +
                                              " stub version should call super.getStubVersion() " +
                                              " template stub version=" + templateStubBaseVersion,
                                         null, myElementType.getClass());
        }
      }

      String baseVersion = myElementType.getExternalId() + ":" + elementTypeStubVersion + ":" + myElementType.getDebugName();
      return myProperties.isEmpty() ? baseVersion : (baseVersion + ":" + StringUtil.join(myProperties, ","));
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

  /**
   * @return corresponding StubFileElementTypes. In some cases it is not possible to get precise StubFileElementType using
   * only version. Providing unique externalId or adding a distinctive debugName when instantiating StubFileElementTypes can help.
   * @implNote this method is very expensive. One should consider implementing caching of results
   */
  public static @NotNull List<StubFileElementType<?>> getStubFileElementTypeFromVersion(@NotNull String version) {
    int externalIdDelimPos = version.indexOf(':');
    if (externalIdDelimPos == -1) {
      LOG.error("Version info is incomplete: " + version);
      externalIdDelimPos = version.length();
    }
    String externalId = version.substring(0, externalIdDelimPos);
    List<StubFileElementType<?>> matches = ContainerUtil.map(IElementType.enumerate(p -> {
      return p instanceof StubFileElementType && ((StubFileElementType<?>)p).getExternalId().equals(externalId);
    }), p -> (StubFileElementType<?>)p);
    if (matches.size() > 1) {
      int stubVersionDelimPos = version.indexOf(':', externalIdDelimPos + 1);
      if (stubVersionDelimPos == -1) {
        LOG.error("Version info is incomplete: " + version);
        return matches;
      }
      int debugNameDelimPos = version.indexOf(':', stubVersionDelimPos + 1);
      if (debugNameDelimPos == -1) debugNameDelimPos = version.length();
      String debugName = version.substring(stubVersionDelimPos + 1, debugNameDelimPos);
      matches = ContainerUtil.filter(matches, p -> {
        return p.getDebugName().equals(debugName);
      });
    }
    return matches;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StubBuilderType type = (StubBuilderType)o;
    return Objects.equals(myElementType, type.myElementType) &&
           Objects.equals(myBinaryFileStubBuilder, type.myBinaryFileStubBuilder) &&
           Objects.equals(myProperties, type.myProperties) &&
           Objects.equals(myBinarySubBuilder, type.myBinarySubBuilder);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myElementType, myBinaryFileStubBuilder, myBinarySubBuilder, myProperties);
  }
}
