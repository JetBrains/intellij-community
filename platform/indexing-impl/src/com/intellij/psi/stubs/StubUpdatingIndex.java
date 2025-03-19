// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.roots.impl.PushedFilePropertiesRetriever;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThreeState;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.storage.TransientChangesIndexStorage;
import com.intellij.util.indexing.storage.MapReduceIndexBase;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMapValueStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.AFTER_SUBSTITUTION;

public final class StubUpdatingIndex extends SingleEntryFileBasedIndexExtension<SerializedStubTree>
  implements CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree> {
  @ApiStatus.Internal
  public static final Logger LOG = Logger.getInstance(StubUpdatingIndex.class);
  private static final boolean DEBUG_PREBUILT_INDICES = SystemProperties.getBooleanProperty("debug.prebuilt.indices", false);

  public static final boolean USE_SNAPSHOT_MAPPINGS = false; //TODO

  private static final int VERSION = 45 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1 : 0);

  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("Stubs");

  private static final FileBasedIndex.ProjectSpecificInputFilter INPUT_FILTER = new BaseFileTypeInputFilter(AFTER_SUBSTITUTION) {
    private static void logIfStubTraceEnabled(@NotNull Supplier<String> logText) {
      if (FileBasedIndex.getInstance() instanceof FileBasedIndexEx fileBasedIndex && FileBasedIndexEx.doTraceStubUpdates(INDEX_ID)) {
        fileBasedIndex.getLogger().info(logText.get());
      }
    }

    private static @Nullable ParserDefinition getParserDefinition(@NotNull FileType fileType, @NotNull Supplier<String> logText) {
      ParserDefinition parserDefinition = null;
      if (fileType instanceof LanguageFileType) {
        Language l = ((LanguageFileType)fileType).getLanguage();
        parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
        if (parserDefinition == null) {
          logIfStubTraceEnabled(() -> "No parser definition for " + logText.get());
        }
      }
      return parserDefinition;
    }

    @Override
    public boolean slowPathIfFileTypeHintUnsure(@NotNull IndexedFile file) {
      if (file.getFileType() instanceof LanguageFileType) {
        ParserDefinition parserDefinition = getParserDefinition(file.getFileType(), file::getFileName);
        if (parserDefinition == null) return false;

        final IFileElementType elementType = parserDefinition.getFileNodeType();
        LanguageStubDescriptor stubDescriptor = StubElementRegistryService.getInstance().getStubDescriptor(elementType.getLanguage());
        if (stubDescriptor != null && stubDescriptor.getStubDefinition().shouldBuildStubFor(file.getFile())) {
          logIfStubTraceEnabled(() -> "Should build stub for " + ((VirtualFileWithId)file.getFile()).getId());
          return true;
        }

        logIfStubTraceEnabled(() -> {
          return "Can't build stub" +
                 ". parserDefinition: " + parserDefinition +
                 ", elementType: " + elementType +
                 ", fileName:" + file.getFileName() +
                 ", properties: " + PushedFilePropertiesRetriever.getInstance().dumpSortedPushedProperties(file.getFile());
        });
      }

      BinaryFileStubBuilder builder = getBinaryStubBuilder(file.getFileType());
      return builder != null && builder.acceptsFile(file.getFile());
    }

    private static @Nullable BinaryFileStubBuilder getBinaryStubBuilder(@NotNull FileType fileType) {
      return BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    }

    @Override
    public @NotNull ThreeState acceptFileType(@NotNull FileType fileType) {
      if (getParserDefinition(fileType, fileType::toString) == null) {
        BinaryFileStubBuilder builder = getBinaryStubBuilder(fileType);
        if (builder == null) return ThreeState.NO;

        VirtualFileFilter builderFileFilter = builder.getFileFilter();
        if (builderFileFilter == VirtualFileFilter.ALL) return ThreeState.YES;
        if (builderFileFilter == VirtualFileFilter.NONE) return ThreeState.NO;
      }

      return ThreeState.UNSURE;
    }
  };

  private final @NotNull StubForwardIndexExternalizer<?> myStubIndexesExternalizer;
  private final @NotNull SerializationManagerEx mySerializationManager;

  @ApiStatus.Internal
  public StubUpdatingIndex() {
    this(StubForwardIndexExternalizer.getIdeUsedExternalizer(), SerializationManagerEx.getInstanceEx());
  }

  @ApiStatus.Internal
  public StubUpdatingIndex(@NotNull StubForwardIndexExternalizer<?> stubIndexesExternalizer,
                           @NotNull SerializationManagerEx serializationManager) {
    myStubIndexesExternalizer = stubIndexesExternalizer;
    mySerializationManager = serializationManager;
  }

  public static boolean canHaveStub(@NotNull VirtualFile file) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    IndexedFile indexedFile = new IndexedFileImpl(file, project);
    return INPUT_FILTER.acceptInput(indexedFile);
  }

  @Override
  public @NotNull ID<Integer, SerializedStubTree> getName() {
    return INDEX_ID;
  }

  @Override
  public @NotNull SingleEntryIndexer<SerializedStubTree> getIndexer() {
    return new SingleEntryCompositeIndexer<SerializedStubTree, StubBuilderType, String>(false) {
      @Override
      public boolean requiresContentForSubIndexerEvaluation(@NotNull IndexedFile file) {
        return StubTreeBuilder.requiresContentToFindBuilder(file.getFileType());
      }

      @Override
      public @Nullable StubBuilderType calculateSubIndexer(@NotNull IndexedFile file) {
        return StubTreeBuilder.getStubBuilderType(file, true);
      }

      @Override
      public @NotNull String getSubIndexerVersion(@NotNull StubBuilderType type) {
        mySerializationManager.initSerializers();
        return type.getVersion();
      }

      @Override
      public @NotNull KeyDescriptor<String> getSubIndexerVersionDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @Override
      protected @Nullable SerializedStubTree computeValue(@NotNull FileContent inputData) {
        StubBuilderType subIndexerType = calculateSubIndexer(inputData);
        if (subIndexerType == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Stub builder not found for " + inputData.getFile() + ", " + calculateIndexingStamp(inputData));
          }
          return null;
        }
        return computeValue(inputData, Objects.requireNonNull(subIndexerType));
      }

      @Override
      protected @Nullable SerializedStubTree computeValue(final @NotNull FileContent inputData, @NotNull StubBuilderType type) {
        try {
          SerializedStubTree prebuiltTree = findPrebuiltSerializedStubTree(inputData);
          if (prebuiltTree != null) {
            prebuiltTree = prebuiltTree.reSerialize(mySerializationManager, myStubIndexesExternalizer);
            if (DEBUG_PREBUILT_INDICES) {
              assertPrebuiltStubTreeMatchesActualTree(prebuiltTree, inputData, type);
            }
            return prebuiltTree;
          }
        } catch (ProcessCanceledException pce) {
          throw pce;
        } catch (Exception e) {
          LOG.error("Error while indexing: " + inputData.getFileName() + " using prebuilt stub index", e);
        }

        Stub stub;
        try {
          stub = StubTreeBuilder.buildStubTree(inputData, type);
        } catch (Exception e) {
          if (e instanceof ControlFlowException) ExceptionUtil.rethrowUnchecked(e);
          throw new MapReduceIndexMappingException(e, type.getClassToBlameInCaseOfException());
        }
        if (stub == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("No stub present for " + inputData.getFile() + ", " + calculateIndexingStamp(inputData));
          }
          return null;
        }

        SerializedStubTree serializedStubTree;
        try {
          serializedStubTree = SerializedStubTree.serializeStub(stub, mySerializationManager, myStubIndexesExternalizer);
          if (IndexDebugProperties.DEBUG) {
            assertDeserializedStubMatchesOriginalStub(serializedStubTree, stub);
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("Stub is built for " + inputData.getFile() + ", " + calculateIndexingStamp(inputData));
          }
        } catch (Exception e) {
          if (e instanceof ControlFlowException) ExceptionUtil.rethrowUnchecked(e);
          ObjectStubSerializer<?, ? extends Stub> stubType = stub.getStubSerializer();
          Class<?> classToBlame = stubType != null ? stubType.getClass() : stub.getClass();
          throw new MapReduceIndexMappingException(e, classToBlame);
        }
        return serializedStubTree;
      }
    };
  }

  private static final FileTypeExtension<PrebuiltStubsProvider> PREBUILT_STUBS_PROVIDER_EP =
    new FileTypeExtension<>("com.intellij.filetype.prebuiltStubsProvider");

  @Override
  public @NotNull DataExternalizer<SerializedStubTree> getValueExternalizer() {
    return new SerializedStubTreeDataExternalizer(mySerializationManager, myStubIndexesExternalizer);
  }

  private static void assertDeserializedStubMatchesOriginalStub(@NotNull SerializedStubTree stubTree,
                                                                @NotNull Stub originalStub) {
    Stub deserializedStub;
    try {
      deserializedStub = stubTree.getStub();
    }
    catch (SerializerNotFoundException e) {
      throw new RuntimeException("Failed to deserialize stub tree", e);
    }
    if (!areStubsSimilar(originalStub, deserializedStub)) {
      LOG.error("original and deserialized trees are not the same",
                new Attachment("originalStub.txt", DebugUtil.stubTreeToString(originalStub)),
                new Attachment("deserializedStub.txt", DebugUtil.stubTreeToString(deserializedStub)));
    }
  }

  private static boolean areStubsSimilar(@NotNull Stub stub, @NotNull Stub stub2) {
    ObjectStubSerializer<?, ? extends Stub> serializer1 = stub.getStubSerializer();
    ObjectStubSerializer<?, ? extends Stub> serializer2 = stub2.getStubSerializer();
    if (!Objects.equals(serializer1, serializer2)) {
      return false;
    }

    IElementType elementType1 = stub instanceof StubElement ? ((StubElement<?>)stub).getElementType() : null;
    IElementType elementType2 = stub2 instanceof StubElement ? ((StubElement<?>)stub2).getElementType() : null;
    if (!Objects.equals(elementType1, elementType2)) {
      return false;
    }

    List<? extends Stub> stubs = stub.getChildrenStubs();
    List<? extends Stub> stubs2 = stub2.getChildrenStubs();

    if (stubs.size() != stubs2.size()) {
      return false;
    }

    for (int i = 0, len = stubs.size(); i < len; ++i) {
      if (!areStubsSimilar(stubs.get(i), stubs2.get(i))) {
        return false;
      }
    }

    return true;
  }

  private void assertPrebuiltStubTreeMatchesActualTree(@NotNull SerializedStubTree prebuiltStubTree,
                                                       @NotNull FileContent fileContent,
                                                       @NotNull StubBuilderType type) {
    try {
      Stub stub = StubTreeBuilder.buildStubTree(fileContent, type);
      if (stub == null) {
        return;
      }
      SerializedStubTree actualTree = SerializedStubTree.serializeStub(stub, mySerializationManager, myStubIndexesExternalizer);
      if (!IndexDataComparer.INSTANCE.areStubTreesTheSame(actualTree, prebuiltStubTree)) {
        throw new RuntimeExceptionWithAttachments(
          "Prebuilt stub tree does not match actual stub tree",
          new Attachment("actual-stub-tree.txt", IndexDataPresenter.INSTANCE.getPresentableSerializedStubTree(actualTree)),
          new Attachment("prebuilt-stub-tree.txt", IndexDataPresenter.INSTANCE.getPresentableSerializedStubTree(prebuiltStubTree))
        );
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public int getCacheSize() {
    return super.getCacheSize() * Runtime.getRuntime().availableProcessors();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull UpdatableIndex<Integer, SerializedStubTree, FileContent, ?> createIndexImplementation(
    @NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension,
    @NotNull VfsAwareIndexStorageLayout<Integer, SerializedStubTree> layout
  ) throws StorageException, IOException {

    ((StubIndexEx)StubIndex.getInstance()).initializeStubIndexes();
    checkNameStorage();
    mySerializationManager.initialize();

    MapReduceIndexBase<Integer, SerializedStubTree, ?> index = StubUpdatableIndexFactory.getInstance().createIndex(extension, new VfsAwareIndexStorageLayout<>() {
      @Override
      public void clearIndexData() {
        layout.clearIndexData();
      }

      @Override
      public @NotNull IndexStorage<Integer, SerializedStubTree> openIndexStorage() throws IOException {
        return layout.openIndexStorage();
      }

      @Override
      public @Nullable ForwardIndex openForwardIndex() throws IOException {
        return layout.openForwardIndex();
      }

      @Override
      public @NotNull ForwardIndexAccessor<Integer, SerializedStubTree> getForwardIndexAccessor() {
        return new StubUpdatingForwardIndexAccessor(extension);
      }
    }, mySerializationManager);
    if (index.getStorage() instanceof TransientChangesIndexStorage<Integer, SerializedStubTree> memStorage) {
      memStorage.addBufferingStateListener(new TransientChangesIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(final boolean newState) {
          ((StubIndexEx)StubIndex.getInstance()).setDataBufferingEnabled(newState);
        }

        @Override
        public void memoryStorageCleared() {
          ((StubIndexEx)StubIndex.getInstance()).cleanupMemoryStorage();
        }
      });
    }
    return index;
  }

  private static @Nullable SerializedStubTree findPrebuiltSerializedStubTree(@NotNull FileContent fileContent) {
    PrebuiltStubsProvider prebuiltStubsProvider = PREBUILT_STUBS_PROVIDER_EP.forFileType(fileContent.getFileType());
    if (prebuiltStubsProvider == null) {
      return null;
    }
    return prebuiltStubsProvider.findStub(fileContent);
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public boolean enableWal() {
    return true;
  }

  @Override
  public void handleInitializationError(@NotNull Throwable e) {
    ((StubIndexEx)StubIndex.getInstance()).initializationFailed(e);
  }

  @ApiStatus.Internal
  public static @NotNull IndexingStampInfo calculateIndexingStamp(@NotNull FileContent content) {
    VirtualFile file = content.getFile();
    boolean isBinary = file.getFileType().isBinary();
    int contentLength = isBinary ? -1 : content.getPsiFile().getTextLength();
    long byteLength = file.getLength();

    return new IndexingStampInfo(file.getTimeStamp(), byteLength, contentLength, isBinary);
  }

  private void checkNameStorage() throws StorageException {
    if (mySerializationManager.isNameStorageCorrupted()) {
      StorageException exception = new StorageException("NameStorage for stubs serialization has been corrupted");
      mySerializationManager.repairNameStorage(exception);
      throw exception;
    }
  }
}
