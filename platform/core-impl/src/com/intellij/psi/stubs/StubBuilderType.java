// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TemplateLanguageStubBaseVersion;
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
  private final LanguageStubDescriptor myStubDescriptor;
  private final List<String> myProperties;
  private final BinaryFileStubBuilder myBinaryFileStubBuilder;
  private final Object myBinarySubBuilder;

  StubBuilderType(@NotNull LanguageStubDescriptor stubDescriptor, @NotNull List<String> properties) {
    myStubDescriptor = stubDescriptor;
    myProperties = properties;
    myBinaryFileStubBuilder = null;
    myBinarySubBuilder = null;
  }

  StubBuilderType(@NotNull BinaryFileStubBuilder binaryFileStubBuilder) {
    myStubDescriptor = null;
    myBinaryFileStubBuilder = binaryFileStubBuilder;
    myBinarySubBuilder = null;
    myProperties = Collections.emptyList();
  }

  StubBuilderType(@NotNull BinaryFileStubBuilder.CompositeBinaryFileStubBuilder binaryFileStubBuilder,
                  @Nullable Object binarySubBuilder) {
    myStubDescriptor = null;
    myBinaryFileStubBuilder = binaryFileStubBuilder;
    myBinarySubBuilder = binarySubBuilder;
    myProperties = Collections.emptyList();
  }

  @NotNull Class<?> getClassToBlameInCaseOfException() {
    if (myStubDescriptor != null) {
      return myStubDescriptor.getFileElementType().getClass();
    }
    if (myBinarySubBuilder == null) {
      return myBinaryFileStubBuilder.getClass();
    }
    return myBinarySubBuilder.getClass();
  }

  BinaryFileStubBuilder getBinaryFileStubBuilder() {
    return myBinaryFileStubBuilder;
  }

  public IFileElementType getFileElementType() {
    return myStubDescriptor != null ? myStubDescriptor.getFileElementType() : null;
  }

  public String getVersion() {
    if (myStubDescriptor != null) {
      int elementTypeStubVersion = myStubDescriptor.getStubDefinition().getStubVersion();

      if (myStubDescriptor.getLanguage() instanceof TemplateLanguage) {
        int templateStubBaseVersion = TemplateLanguageStubBaseVersion.getVersion();
        if (elementTypeStubVersion < templateStubBaseVersion) {
          PluginException.logPluginError(LOG, myStubDescriptor.getFileElementType().getClass() + " " +
                                              myStubDescriptor.getLanguage() +
                                              " version=" + elementTypeStubVersion + " " +
                                              " stub version should call super.getStubVersion() " +
                                              " template stub version=" + templateStubBaseVersion,
                                         null, myStubDescriptor.getFileElementType().getClass());
        }
      }

      String baseVersion = myStubDescriptor.getFileElementSerializer().getExternalId() + ":" + elementTypeStubVersion + ":" + myStubDescriptor.getFileElementType().getDebugName();
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
  public static @NotNull List<IFileElementType> getStubFileElementTypeFromVersion(@NotNull String version) {
    int externalIdDelimPos = version.indexOf(':');
    if (externalIdDelimPos == -1) {
      LOG.error("Version info is incomplete: " + version);
      externalIdDelimPos = version.length();
    }
    String externalId = version.substring(0, externalIdDelimPos);
    StubElementRegistryService stubElementRegistryService = StubElementRegistryService.getInstance();
    List<IFileElementType> matches = IElementType.mapNotNull(type -> {
      if (!(type instanceof IFileElementType)) {
        return null;
      }
      ObjectStubSerializer<?, @NotNull Stub> serializer = stubElementRegistryService.getStubSerializer(type);
      if (serializer == null || !serializer.getExternalId().equals(externalId)) {
        return null;
      }
      return ((IFileElementType)type);
    });
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
    return Objects.equals(myStubDescriptor.getFileElementType(), type.myStubDescriptor.getFileElementType()) &&
           Objects.equals(myBinaryFileStubBuilder, type.myBinaryFileStubBuilder) &&
           Objects.equals(myProperties, type.myProperties) &&
           Objects.equals(myBinarySubBuilder, type.myBinarySubBuilder);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFileElementType(), myBinaryFileStubBuilder, myBinarySubBuilder, myProperties);
  }
}
