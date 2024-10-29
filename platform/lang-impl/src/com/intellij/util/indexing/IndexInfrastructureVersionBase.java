// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.google.common.collect.ImmutableSortedMap;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.stubs.StubIndexExtension;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class IndexInfrastructureVersionBase {
  private static final Logger LOG = Logger.getInstance(IndexInfrastructureVersionBase.class);
  // base versions: it is required to have 100% match on that indexes in order to load it to an IDE.
  protected final @NotNull SortedMap<String, String> myBaseIndexes;

  // files-based indexes versions: indexes are loadable even if some indexes does not match
  protected final @NotNull SortedMap<String, FileBasedIndexVersionInfo> myFileBasedIndexVersions;

  // stub indexes versions: it is on if some indexes does not match
  protected final @NotNull SortedMap<String, String> myStubIndexVersions;

  // stub file element type versions:
  protected final @NotNull SortedMap<String, String> myStubFileElementTypeVersions;

  // file type name -> composite version of binary file stub builders associated with this file type
  // see [comStubSharedIndexExtension.getBinaryFileStubBuilderVersion]
  protected final @NotNull SortedMap<String, String> myCompositeBinaryStubFileBuilderVersions;

  public IndexInfrastructureVersionBase(@NotNull Map<String, String> baseIndexes,
                                        @NotNull Map<String, FileBasedIndexVersionInfo> fileBasedIndexVersions,
                                        @NotNull Map<String, String> stubIndexVersions,
                                        @NotNull Map<String, String> stubFileElementTypeVersions,
                                        @NotNull Map<String, String> compositeBinaryStubFileBuilderVersions) {
    myBaseIndexes = ImmutableSortedMap.copyOf(baseIndexes);
    myFileBasedIndexVersions = ImmutableSortedMap.copyOf(fileBasedIndexVersions);
    myStubIndexVersions = ImmutableSortedMap.copyOf(stubIndexVersions);
    myStubFileElementTypeVersions = ImmutableSortedMap.copyOf(stubFileElementTypeVersions);
    myCompositeBinaryStubFileBuilderVersions = ImmutableSortedMap.copyOf(compositeBinaryStubFileBuilderVersions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IndexInfrastructureVersionBase base = (IndexInfrastructureVersionBase)o;
    return myBaseIndexes.equals(base.myBaseIndexes) &&
           myFileBasedIndexVersions.equals(base.myFileBasedIndexVersions) &&
           myStubIndexVersions.equals(base.myStubIndexVersions) &&
           myStubFileElementTypeVersions.equals(base.myStubFileElementTypeVersions) &&
           myCompositeBinaryStubFileBuilderVersions.equals(base.myCompositeBinaryStubFileBuilderVersions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myBaseIndexes, myFileBasedIndexVersions, myStubIndexVersions, myStubFileElementTypeVersions,
                        myCompositeBinaryStubFileBuilderVersions);
  }


  public static @NotNull Map<String, FileBasedIndexVersionInfo> fileBasedIndexVersions(
    @NotNull List<? extends FileBasedIndexExtension<?, ?>> fileBasedIndexExtensions,
    @NotNull Function<? super FileBasedIndexExtension<?, ?>, String> versionExtractor
  ) {
    var builder = new HashMap<String, FileBasedIndexVersionInfo>();
    for (FileBasedIndexExtension<?, ?> extension : fileBasedIndexExtensions) {

      ID<?, ?> indexId = extension.getName();
      String name = indexId.getName();
      var newValue = new FileBasedIndexVersionInfo(
        versionExtractor.fun(extension),
        extension.needsForwardIndexWhenSharing()
      );

      var oldValue = builder.put(name, newValue);

      if (oldValue != null && !oldValue.equals(newValue)) {
        LOG.warn("Multiple declarations of the same file based index: " + name + ", old value " + oldValue + ", new value: " + newValue);
      }
    }
    return builder;
  }

  public static @NotNull Map<String, String> stubIndexVersions(@NotNull List<? extends StubIndexExtension<?, ?>> stubIndexExtensions) {
    var builder = new HashMap<String, String>();

    FileBasedIndexExtension<?, ?> stubUpdatingIndex =
      FileBasedIndexExtension.EXTENSION_POINT_NAME.findFirstSafe(ex -> ex.getName().equals(StubUpdatingIndex.INDEX_ID));

    if (stubUpdatingIndex == null) {
      LOG.warn("Failed to find " + StubUpdatingIndex.INDEX_ID);
      return Collections.emptyMap();
    }

    String commonPrefix = stubUpdatingIndex.getVersion() + ":";
    for (StubIndexExtension<?, ?> ex : stubIndexExtensions) {
      String name = ex.getKey().getName();
      String newValue = commonPrefix + ex.getVersion();
      var oldValue = builder.put(name, newValue);
      if (oldValue != null && !oldValue.equals(newValue)) {
        LOG.warn("Multiple declarations of the same stub based index: " + name + ", old value " + oldValue + ", new value: " + newValue);
      }
    }

    return builder;
  }

  public static @NotNull Map<String, String> getAllCompositeBinaryFileStubBuilderVersions() {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<FileType, BinaryFileStubBuilder> entry : BinaryFileStubBuilders.INSTANCE.getAllRegisteredExtensions().entrySet()) {
      BinaryFileStubBuilder builder = entry.getValue();
      if (builder instanceof BinaryFileStubBuilder.CompositeBinaryFileStubBuilder) {
        //noinspection unchecked
        result.put(
          entry.getKey().getName(),
          getBinaryFileStubBuilderVersion((BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Object>)builder)
        );
      }
    }
    return result;
  }

  public static @NotNull String getBinaryFileStubBuilderVersion(@NotNull BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?> builder) {
    BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Object> genericBuilder =
      (BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Object>)builder;
    return builder.getClass().getName() + ":" + builder.getStubVersion() + ";" +
           genericBuilder.getAllSubBuilders()
             .map(b -> genericBuilder.getSubBuilderVersion(b))
             .sorted().collect(Collectors.joining(";"));
  }

  public static @NotNull Map<String, String> stubFileElementTypeVersions() {
    var builder = new HashMap<String, String>();

    for (IFileElementType fileElementType : getAllStubFileElementTypes()) {
      if (fileElementType instanceof IStubFileElementType) {
        int stubVersion = getStubFileElementBaseVersion((IStubFileElementType<?>)fileElementType);
        String name = getStubFileElementTypeKey((IStubFileElementType<?>)fileElementType);

        String newVersion = Integer.toString(stubVersion);

        var oldValue = builder.put(name, newVersion);
        if (oldValue != null && !oldValue.equals(newVersion)) {
          LOG.warn("Multiple declarations of the same IFileElementType: " + name + ", old version " + oldValue + ", new version: " + newVersion);
        }
      }
    }

    return builder;
  }

  public static @NotNull List<IFileElementType> getAllStubFileElementTypes() {
    return Arrays.stream(FileTypeManager.getInstance().getRegisteredFileTypes())
      .filter(type -> type instanceof LanguageFileType)
      .map(type -> ((LanguageFileType)type).getLanguage())
      .map(LanguageParserDefinitions.INSTANCE::forLanguage)
      .filter(Objects::nonNull)
      .map(ParserDefinition::getFileNodeType)
      .collect(Collectors.toList());
  }

  public static @NotNull String getStubFileElementTypeKey(@NotNull IStubFileElementType<?> fileNodeType) {
    return fileNodeType.getExternalId() + ":" + fileNodeType.getLanguage().getID();
  }

  public static int getStubFileElementBaseVersion(@NotNull IStubFileElementType<?> fileNodeType) {
    int stubVersion = fileNodeType.getStubVersion();
    return fileNodeType.getLanguage() instanceof TemplateLanguage
           ? stubVersion - IStubFileElementType.getTemplateStubBaseVersion()
           : stubVersion;
  }
}
