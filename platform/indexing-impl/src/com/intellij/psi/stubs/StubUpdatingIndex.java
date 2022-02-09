// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.roots.impl.PushedFilePropertiesRetriever;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.*;
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
import java.util.function.Consumer;

public final class StubUpdatingIndex extends SingleEntryFileBasedIndexExtension<SerializedStubTree>
  implements CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree> {
  @ApiStatus.Internal
  static final Logger LOG = Logger.getInstance(StubUpdatingIndex.class);
  private static final boolean DEBUG_PREBUILT_INDICES = SystemProperties.getBooleanProperty("debug.prebuilt.indices", false);

  public static final boolean USE_SNAPSHOT_MAPPINGS = false; //TODO

  private static final int VERSION = 45 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1 : 0);

  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("Stubs");

  @NotNull
  private final StubForwardIndexExternalizer<?> myStubIndexesExternalizer;

  @NotNull
  private final SerializationManagerEx mySerializationManager;

  public StubUpdatingIndex() {
    this(StubForwardIndexExternalizer.getIdeUsedExternalizer(), SerializationManagerEx.getInstanceEx());
  }

  public StubUpdatingIndex(@NotNull StubForwardIndexExternalizer<?> stubIndexesExternalizer,
                           @NotNull SerializationManagerEx serializationManager) {
    myStubIndexesExternalizer = stubIndexesExternalizer;
    mySerializationManager = serializationManager;
  }

  public static boolean canHaveStub(@NotNull VirtualFile file) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    FileType fileType = SubstitutedFileType.substituteFileType(file, file.getFileType(), project);
    return canHaveStub(file, fileType);
  }

  private static boolean canHaveStub(@NotNull VirtualFile file, @NotNull FileType fileType) {
    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      FileBasedIndexEx fileBasedIndex = ObjectUtils.tryCast(FileBasedIndex.getInstance(), FileBasedIndexEx.class);

      if (parserDefinition == null) {
        if (fileBasedIndex != null && fileBasedIndex.doTraceStubUpdates(StubUpdatingIndex.INDEX_ID)) {
          fileBasedIndex.getLogger().info("No parser definition for " + file.getName());
        }
        return false;
      }

      final IFileElementType elementType = parserDefinition.getFileNodeType();
      if (elementType instanceof IStubFileElementType && ((IStubFileElementType<?>)elementType).shouldBuildStubFor(file)) {
        if (fileBasedIndex != null && fileBasedIndex.doTraceStubUpdates(StubUpdatingIndex.INDEX_ID)) {
          fileBasedIndex.getLogger().info("Should build stub for " + file.getName());
        }
        return true;
      }

      if (fileBasedIndex != null && fileBasedIndex.doTraceStubUpdates(StubUpdatingIndex.INDEX_ID)) {
        fileBasedIndex.getLogger().info("Can't build stub using stub file element type " + file.getName() +
                                        ", properties: " + PushedFilePropertiesRetriever.getInstance().dumpSortedPushedProperties(file));
      }
    }
    final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    return builder != null && builder.acceptsFile(file);
  }

  @NotNull
  @Override
  public ID<Integer, SerializedStubTree> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public SingleEntryIndexer<SerializedStubTree> getIndexer() {
    return new SingleEntryCompositeIndexer<SerializedStubTree, StubBuilderType, String>(false) {
      @Override
      public boolean requiresContentForSubIndexerEvaluation(@NotNull IndexedFile file) {
        return StubTreeBuilder.requiresContentToFindBuilder(file.getFileType());
      }

      @Nullable
      @Override
      public StubBuilderType calculateSubIndexer(@NotNull IndexedFile file) {
        return StubTreeBuilder.getStubBuilderType(file, true);
      }

      @NotNull
      @Override
      public String getSubIndexerVersion(@NotNull StubBuilderType type) {
        return type.getVersion();
      }

      @NotNull
      @Override
      public KeyDescriptor<String> getSubIndexerVersionDescriptor() {
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
      @Nullable
      protected SerializedStubTree computeValue(@NotNull final FileContent inputData, @NotNull StubBuilderType type) {
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
          ObjectStubSerializer<?, ? extends Stub> stubType = stub.getStubType();
          Class<?> classToBlame = stubType != null ? stubType.getClass() : stub.getClass();
          throw new MapReduceIndexMappingException(e, classToBlame);
        }
        return serializedStubTree;
      }
    };
  }

  private static final FileTypeExtension<PrebuiltStubsProvider> PREBUILT_STUBS_PROVIDER_EP =
    new FileTypeExtension<>("com.intellij.filetype.prebuiltStubsProvider");

  @Nullable
  private static SerializedStubTree findPrebuiltSerializedStubTree(@NotNull FileContent fileContent) {
    PrebuiltStubsProvider prebuiltStubsProvider = PREBUILT_STUBS_PROVIDER_EP.forFileType(fileContent.getFileType());
    if (prebuiltStubsProvider == null) {
      return null;
    }
    return prebuiltStubsProvider.findStub(fileContent);
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
    if (stub.getStubType() != stub2.getStubType()) {
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

  @NotNull
  static IndexingStampInfo calculateIndexingStamp(@NotNull FileContent content) {
    VirtualFile file = content.getFile();
    boolean isBinary = file.getFileType().isBinary();
    int contentLength = isBinary ? -1 : content.getPsiFile().getTextLength();
    long byteLength = file.getLength();

    return new IndexingStampInfo(file.getTimeStamp(), byteLength, contentLength, isBinary);
  }

  @Override
  public int getCacheSize() {
    return super.getCacheSize() * Runtime.getRuntime().availableProcessors();
  }

  @NotNull
  @Override
  public DataExternalizer<SerializedStubTree> getValueExternalizer() {
    ensureSerializationManagerInitialized(mySerializationManager);
    return new SerializedStubTreeDataExternalizer(mySerializationManager, myStubIndexesExternalizer);
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.ProjectSpecificInputFilter() {
      @Override
      public boolean acceptInput(@NotNull IndexedFile file) {
        return canHaveStub(file.getFile(), file.getFileType());
      }
    };
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

  @NotNull
  @Override
  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(@NotNull final FileBasedIndexExtension<Integer, SerializedStubTree> extension,
                                                                                            @NotNull VfsAwareIndexStorageLayout<Integer, SerializedStubTree> layout)
    throws StorageException, IOException {
    ((StubIndexEx)StubIndex.getInstance()).initializeStubIndexes();
    checkNameStorage();
    mySerializationManager.initialize();

    MapReduceIndexBase<Integer, SerializedStubTree> index = StubUpdatableIndexFactory.getInstance().createIndex(extension, new VfsAwareIndexStorageLayout<>() {
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
    if (index.getStorage() instanceof TransientChangesIndexStorage) {
      final TransientChangesIndexStorage<Integer, SerializedStubTree>
        memStorage = (TransientChangesIndexStorage<Integer, SerializedStubTree>)index.getStorage();
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

  private void checkNameStorage() throws StorageException {
    if (mySerializationManager.isNameStorageCorrupted()) {
      StorageException exception = new StorageException("NameStorage for stubs serialization has been corrupted");
      mySerializationManager.repairNameStorage(exception);
      throw exception;
    }
  }

  private static void ensureSerializationManagerInitialized(@NotNull SerializationManagerEx serializationManager) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      instantiateElementTypesFromFields();
      StubIndexEx.initExtensions();
      serializationManager.initSerializers();
    });
  }

  private static void instantiateElementTypesFromFields() {
    // load stub serializers before usage
    FileTypeRegistry.getInstance().getRegisteredFileTypes();
    getExtensions(BinaryFileStubBuilders.INSTANCE, builder -> {});
    getExtensions(LanguageParserDefinitions.INSTANCE, ParserDefinition::getFileNodeType);
  }

  private static <T> void getExtensions(@NotNull KeyedExtensionCollector<T, ?> collector, @NotNull Consumer<? super T> consumer) {
    ExtensionPointImpl<KeyedLazyInstance<T>> point = (ExtensionPointImpl<KeyedLazyInstance<T>>)collector.getPoint();
    if (point != null) {
      for (KeyedLazyInstance<T> instance : point) {
        consumer.accept(instance.getInstance());
      }
    }
  }
}
