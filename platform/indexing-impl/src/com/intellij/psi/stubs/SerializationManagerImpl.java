// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TemplateLanguageStubBaseVersion;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.InMemoryDataEnumerator;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.util.io.PersistentHashMapValueStorage.CreationTimeOptions;
import static java.util.Comparator.comparing;

// todo rewrite: it's an app service for now but its lifecycle should be synchronized with stub index.
@ApiStatus.Internal
public final class SerializationManagerImpl extends SerializationManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(SerializationManagerImpl.class);

  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean();
  private final @NotNull Supplier<? extends Path> myFile;
  private final boolean myUnmodifiable;
  private final AtomicBoolean myInitialized = new AtomicBoolean();

  private volatile Path myOpenFile;
  private volatile StubSerializationHelper myStubSerializationHelper;
  private volatile StubSerializerEnumerator mySerializerEnumerator;
  private volatile boolean mySerializersLoaded;

  public SerializationManagerImpl() {
    this(() -> FileBasedIndex.USE_IN_MEMORY_INDEX ? null : PathManager.getIndexRoot().resolve("rep.names"), false);
  }

  @NonInjectable
  public SerializationManagerImpl(@NotNull Path nameStorageFile, boolean unmodifiable) {
    this(() -> nameStorageFile, unmodifiable);
  }

  @NonInjectable
  public SerializationManagerImpl(@NotNull Supplier<? extends @Nullable Path> nameStorageFile, boolean unmodifiable) {
    myFile = nameStorageFile;
    myUnmodifiable = unmodifiable;
    initialize();
    StubElementTypeHolderEP.EP_NAME.addChangeListener(this::dropSerializerData, this);
    // todo IJPL-562 is this correct???
    StubElementRegistryServiceImplKt.STUB_REGISTRY_EP.addChangeListener(this::dropSerializerData, this);
    ExtensionPoint<@NotNull KeyedLazyInstance<@NotNull LanguageStubDefinition>> point = StubElementRegistryServiceImplKt.STUB_DEFINITION_EP.getPoint();
    if (point != null) {
      point.addChangeListener(this::dropSerializerData, this);
    }
  }

  @Override
  public void initialize() {
    if (myInitialized.get()) return;
    doInitialize();
  }

  private void doInitialize() {
    try {
      // we need to cache last id -> String mappings due to StringRefs and stubs indexing that initially creates stubs (doing enumerate on String)
      // and then index them (valueOf), also similar string items are expected to be enumerated during stubs processing
      StubSerializerEnumerator enumerator = new StubSerializerEnumerator(openNameStorage(), myUnmodifiable);
      mySerializerEnumerator = enumerator;
      myStubSerializationHelper = new StubSerializationHelper(enumerator);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
    }
    finally {
      myInitialized.set(true);
    }
  }

  private @NotNull DataEnumeratorEx<String> openNameStorage() throws IOException {
    myOpenFile = myFile.get();
    if (myOpenFile == null) {
      return new InMemoryDataEnumerator<>();
    }

    return CreationTimeOptions.threadLocalOptions()
      .readOnly(myUnmodifiable)
      .with(() -> {
        return new PersistentStringEnumerator(myOpenFile, /*cacheLastMapping: */ true);
      });
  }

  @ApiStatus.Internal
  public Map<String, Integer> dumpNameStorage() {
    return mySerializerEnumerator.dump();
  }

  @Override
  public boolean isNameStorageCorrupted() {
    return myNameStorageCrashed.get();
  }

  @Override
  public void repairNameStorage(@NotNull Exception corruptionCause) {
    if (myNameStorageCrashed.getAndSet(false)) {
      if (myUnmodifiable) {
        LOG.error("Data provided by unmodifiable serialization manager can be invalid after repair");
      }
      LOG.info("Name storage is repaired");

      StubSerializerEnumerator enumerator = mySerializerEnumerator;
      if (enumerator != null) {
        try {
          enumerator.close();
        }
        catch (Exception ignored) {}
      }
      if (myOpenFile != null) {
        IOUtil.deleteAllFilesStartingWith(myOpenFile);
      }
      doInitialize();
    }
  }

  @Override
  public void flushNameStorage() throws IOException {
    mySerializerEnumerator.flush();
  }

  private void registerSerializer(ObjectStubSerializer<?, ? extends Stub> serializer) {
    registerSerializer(serializer.getExternalId(), () -> serializer);
  }

  @Override
  public void reinitializeNameStorage() {
    nameStorageCrashed();
    repairNameStorage(new Exception("Indexes are requested to rebuild"));
  }

  private void nameStorageCrashed() {
    myNameStorageCrashed.set(true);
  }

  @Override
  public void dispose() {
    performShutdown();
  }

  @Override
  public void performShutdown() {
    if (!myInitialized.compareAndSet(true, false)) {
      return; // already shut down
    }
    String name = myOpenFile != null ? myOpenFile.toString() : "in-memory storage";
    if (!myUnmodifiable) {
      LOG.info("Start shutting down " + name);
    }
    try {
      mySerializerEnumerator.close();
      if (!myUnmodifiable) {
        LOG.info("Finished shutting down " + name);
      }
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.error(e);
    }
  }

  private void registerSerializer(@NotNull String externalId, @NotNull Supplier<? extends ObjectStubSerializer<?, ? extends Stub>> lazySerializer) {
    try {
      mySerializerEnumerator.assignId(lazySerializer, externalId);
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
  }

  @Override
  public void serialize(@NotNull Stub rootStub, @NotNull OutputStream stream) {
    initSerializers();
    try {
      myStubSerializationHelper.serialize(rootStub, stream);
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
  }

  @Override
  public @NotNull Stub deserialize(@NotNull InputStream stream) throws SerializerNotFoundException {
    initSerializers();

    try {
      return myStubSerializationHelper.deserialize(stream);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void reSerialize(@NotNull InputStream inStub,
                          @NotNull OutputStream outStub,
                          @NotNull StubTreeSerializer newSerializationManager) throws IOException {
    initSerializers();
    ((SerializationManagerEx)newSerializationManager).initSerializers();
    myStubSerializationHelper.reSerializeStub(new DataInputStream(inStub),
                                              new DataOutputStream(outStub),
                                              ((SerializationManagerImpl)newSerializationManager).myStubSerializationHelper);
  }

  @Override
  protected void initSerializers() {
    if (mySerializersLoaded) return;
    //noinspection SynchronizeOnThis
    synchronized (this) {
      if (mySerializersLoaded) {
        return;
      }

      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        instantiateElementTypesFromFields();
        StubIndexEx.initExtensions();
      });

      registerSerializer(PsiFileStubImpl.TYPE);

      final List<StubFieldAccessor> lazySerializers = IStubElementType.loadRegisteredStubElementTypes();

      record TypeAndSerializer(
        @NotNull IElementType type,
        @NotNull ObjectStubSerializer<?, Stub> serializer
      ) {}

      StubElementRegistryService stubElementRegistryService = StubElementRegistryService.getInstance();
      List<TypeAndSerializer> stubElementTypes = new ArrayList<>(IElementType.mapNotNull(type -> {
        ObjectStubSerializer<?, @NotNull Stub> serializer = stubElementRegistryService.getStubSerializer(type);
        if (serializer == null) {
          return null;
        }
        return new TypeAndSerializer(type, serializer);
      }));

      stubElementTypes.sort(
        comparing((TypeAndSerializer tas) -> tas.type.getLanguage().getID())
          //TODO RC: not sure .debugName is enough for stable sorting. Maybe use .getClass() instead?
          .thenComparing(tas -> tas.type.getDebugName())
      );

      for (TypeAndSerializer tas : stubElementTypes) {
        ObjectStubSerializer<?, Stub> serializer = tas.serializer;
        if (StubSerializerId.DEFAULT_EXTERNAL_ID.equals(serializer.getExternalId())) {
          continue;
        }

        registerSerializer(serializer);
      }

      final List<StubFieldAccessor> sortedLazySerializers = lazySerializers.stream()
        //TODO RC: is .externalId enough for stable sorting? Seems like .myField is also important,
        //         but it should also be dependent on .externalId...
        .sorted(comparing(sfa -> sfa.externalId))
        .toList();
      for (StubFieldAccessor lazySerializer : sortedLazySerializers) {
        registerSerializer(lazySerializer.externalId, lazySerializer);
      }
      mySerializersLoaded = true;
    }
  }

  @NotNull ObjectStubSerializer<?, ? extends Stub> getSerializer(@NotNull String name) throws SerializerNotFoundException {
    return mySerializerEnumerator.getSerializer(name);
  }

  public @Nullable String getSerializerName(@NotNull ObjectStubSerializer<?, ? extends Stub> serializer) {
    return mySerializerEnumerator.getSerializerName(serializer);
  }

  public void dropSerializerData() {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      IStubElementType.dropRegisteredTypes();
      TemplateLanguageStubBaseVersion.dropVersion();
      StubSerializerEnumerator enumerator = mySerializerEnumerator;
      if (enumerator != null) {
        enumerator.dropRegisteredSerializers();
      }
      else {
        // has been corrupted previously
        nameStorageCrashed();
      }
      mySerializersLoaded = false;
    }
  }

  private static void instantiateElementTypesFromFields() {
    // load stub serializers before usage
    FileTypeRegistry.getInstance().getRegisteredFileTypes();
    getExtensions(BinaryFileStubBuilders.INSTANCE, builder -> {});
    getExtensions(LanguageParserDefinitions.INSTANCE, ParserDefinition::getFileNodeType);
  }

  private static <T> void getExtensions(@NotNull KeyedExtensionCollector<T, ?> collector, @NotNull Consumer<? super T> consumer) {
    ExtensionPointImpl<@NotNull KeyedLazyInstance<T>> point = (ExtensionPointImpl<@NotNull KeyedLazyInstance<T>>)collector.getPoint();
    if (point != null) {
      Iterator<KeyedLazyInstance<T>> iterator = point.iterator();
      while (iterator.hasNext()) {
        consumer.accept(iterator.next().getInstance());
      }
    }
  }
}
