// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilesScanExecutor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static com.intellij.util.indexing.FileBasedIndex.getFileId;

public final class FileBasedIndexScanUtil {

  private static void ensureIdFilterUpToDate() {
    ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getChangedFilesCollector().processFilesToUpdateInReadAction();
  }

  private static class InThisThreadProcessor {
    final Thread thread = Thread.currentThread();
    final ConcurrentLinkedQueue<BooleanSupplier> queue = new ConcurrentLinkedQueue<>();

    boolean process(@NotNull BooleanSupplier r) {
      if (Thread.currentThread() != thread) {
        queue.add(r);
        return true;
      }
      if (!processQueue()) return false;
      return r.getAsBoolean();
    }

    boolean processQueue() {
      BooleanSupplier polled;
      while ((polled = queue.poll()) != null) {
        if (!polled.getAsBoolean()) return false;
      }
      return true;
    }
  }

  static <K> @Nullable Boolean processAllKeys(@NotNull ID<K, ?> indexId,
                                              @NotNull Processor<? super K> processor,
                                              @NotNull GlobalSearchScope scope,
                                              @Nullable IdFilter idFilter) {
    if (indexId == FilenameIndex.NAME && Registry.is("indexing.filename.over.vfs")) {
      ensureIdFilterUpToDate();
      //noinspection unchecked
      return FSRecords.processAllNames((Processor<String>)processor);
    }
    else if (indexId == FileTypeIndex.NAME && Registry.is("indexing.filetype.over.vfs")) {
      ensureIdFilterUpToDate();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      if (!FilesScanExecutor.processFilesInScope(scope, true, file -> {
        if (idFilter != null && !idFilter.containsFileId(getFileId(file))) return true;
        //noinspection unchecked
        K fileType = (K)file.getFileType();
        return threadProcessor.process(()-> processor.process(fileType));
      })) return false;
      return threadProcessor.processQueue();
    }
    else if (indexId == TodoIndex.NAME && Registry.is("indexing.todo.over.vfs") ||
             indexId == IdIndex.NAME && Registry.is("indexing.id.over.vfs")) {
      ensureIdFilterUpToDate();
      Project project = scope.getProject();
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      FileBasedIndexExtension<K, ?> indexExtension = Objects.requireNonNull(findIndexExtension(indexId));
      FileBasedIndex.InputFilter inputFilter = indexExtension.getInputFilter();
      DataIndexer<K, ?, FileContent> indexer = indexExtension.getIndexer();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      if (!FilesScanExecutor.processFilesInScope(scope, false, file -> {
        if (idFilter != null && !idFilter.containsFileId(getFileId(file))) return true;
        if (!FileBasedIndexEx.acceptsInput(inputFilter, new IndexedFileImpl(file, project))) return true;
        Document document = fileDocumentManager.getCachedDocument(file);
        CharSequence s = document != null ? document.getCharsSequence() : LoadTextUtil.loadText(file, -1);
        Map<K, ?> map = indexer.map(FileContentImpl.createByText(file, s, project));
        Collection<K> keys = map.keySet();
        return threadProcessor.process(() -> ContainerUtil.process(keys, processor));
      })) return false;
      return threadProcessor.processQueue();
    }
    return null;
  }

  static <K, V> @Nullable Boolean processValuesInScope(@NotNull ID<K, V> indexId,
                                                       @NotNull K dataKey,
                                                       boolean ensureValueProcessedOnce,
                                                       @NotNull GlobalSearchScope scope,
                                                       @Nullable IdFilter idFilter,
                                                       @NotNull FileBasedIndex.ValueProcessor<? super V> processor) {
    if (indexId == FilenameIndex.NAME && Registry.is("indexing.filename.over.vfs")) {
      ensureIdFilterUpToDate();
      IntOpenHashSet ids = new IntOpenHashSet();
      FSRecords.processFilesWithNames(Set.of((String)dataKey), id -> {
        if (idFilter != null && !idFilter.containsFileId(id)) return true;
        ids.add(id);
        return true;
      });
      PersistentFS fs = PersistentFS.getInstance();
      IntIterator iterator = ids.iterator();
      while (iterator.hasNext()) {
        VirtualFile file = fs.findFileById(iterator.nextInt());
        if (file == null || !scope.contains(file)) continue;
        if (!processor.process(file, null)) return false;
        if (ensureValueProcessedOnce) break;
      }
      return true;
    }
    else if (indexId == FileTypeIndex.NAME && Registry.is("indexing.filetype.over.vfs")) {
      ensureIdFilterUpToDate();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      Ref<Boolean> stoppedByVal = ensureValueProcessedOnce ? Ref.create(false) : null;
      if (!FilesScanExecutor.processFilesInScope(scope, true, file -> {
        if (idFilter != null && !idFilter.containsFileId(getFileId(file))) return true;
        if (!Objects.equals(dataKey, file.getFileType())) return true;
        if (!threadProcessor.process(() -> processor.process(file, null))) return false;
        if (ensureValueProcessedOnce) {
          stoppedByVal.set(true);
          return false;
        }
        return true;
      }) && !(ensureValueProcessedOnce && stoppedByVal.get())) return false;
      return threadProcessor.processQueue();
    }
    else if (indexId == TodoIndex.NAME && Registry.is("indexing.todo.over.vfs") ||
             indexId == IdIndex.NAME && Registry.is("indexing.id.over.vfs")) {
      ensureIdFilterUpToDate();
      Project project = scope.getProject();
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      FileBasedIndexExtension<K, V> indexExtension = Objects.requireNonNull(findIndexExtension(indexId));
      FileBasedIndex.InputFilter inputFilter = indexExtension.getInputFilter();
      DataIndexer<K, V, FileContent> indexer = indexExtension.getIndexer();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      ConcurrentHashMap<V, Boolean> visitedValues = ensureValueProcessedOnce ? new ConcurrentHashMap<>() : null;
      if (!FilesScanExecutor.processFilesInScope(scope, false, file -> {
        if (idFilter != null && !idFilter.containsFileId(getFileId(file))) return true;
        if (!FileBasedIndexEx.acceptsInput(inputFilter, new IndexedFileImpl(file, project))) return true;
        Document document = fileDocumentManager.getCachedDocument(file);
        CharSequence s = document != null ? document.getCharsSequence() : LoadTextUtil.loadText(file, -1);
        Map<K, V> map = indexer.map(FileContentImpl.createByText(file, s, project));
        V value = map.get(dataKey);
        if (value == null) return true;
        if (ensureValueProcessedOnce && visitedValues.put(value, true) != null) return true;
        return threadProcessor.process(() -> processor.process(file, value));
      })) return false;
      return threadProcessor.processQueue();
    }
    return null;
  }

  private static <K, V> @Nullable FileBasedIndexExtension<K, V> findIndexExtension(@NotNull ID<K, V> id) {
    for (FileBasedIndexExtension<?, ?> extension : FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList()) {
      if (extension.getName() == id) return (FileBasedIndexExtension<K, V>)extension;
    }
    return null;
  }

  public static <K, V> Boolean processValuesInOneFile(@NotNull ID<K, V> indexId,
                                                      @NotNull K dataKey,
                                                      @NotNull VirtualFile file,
                                                      @NotNull GlobalSearchScope scope,
                                                      @NotNull FileBasedIndex.ValueProcessor<? super V> processor) {
    if (indexId == TodoIndex.NAME && Registry.is("indexing.todo.over.vfs") ||
        indexId == IdIndex.NAME && Registry.is("indexing.id.over.vfs")) {
      Project project = scope.getProject();
      FileBasedIndexExtension<K, V> indexExtension = Objects.requireNonNull(findIndexExtension(indexId));
      FileBasedIndex.InputFilter inputFilter = indexExtension.getInputFilter();
      DataIndexer<K, V, FileContent> indexer = indexExtension.getIndexer();
      if (!FileBasedIndexEx.acceptsInput(inputFilter, new IndexedFileImpl(file, project))) return true;
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      Document document = fileDocumentManager.getCachedDocument(file);
      CharSequence s = document != null ? document.getCharsSequence() : LoadTextUtil.loadText(file, -1);
      Map<K, V> map = indexer.map(FileContentImpl.createByText(file, s, project));
      V value = map.get(dataKey);
      if (value == null) return true;
      return processor.process(file, value);
    }
    return null;
  }

  public static <K, V> Boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                             @NotNull Collection<? extends K> dataKeys,
                                                             @NotNull GlobalSearchScope scope,
                                                             @Nullable Condition<? super V> valueChecker,
                                                             @NotNull Processor<? super VirtualFile> processor) {
    if (indexId == TodoIndex.NAME && Registry.is("indexing.todo.over.vfs") ||
        indexId == IdIndex.NAME && Registry.is("indexing.id.over.vfs")) {
      ensureIdFilterUpToDate();
      Project project = scope.getProject();
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      FileBasedIndexExtension<K, V> indexExtension = Objects.requireNonNull(findIndexExtension(indexId));
      FileBasedIndex.InputFilter inputFilter = indexExtension.getInputFilter();
      DataIndexer<K, V, FileContent> indexer = indexExtension.getIndexer();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      if (!FilesScanExecutor.processFilesInScope(scope, false, file -> {
        if (!FileBasedIndexEx.acceptsInput(inputFilter, new IndexedFileImpl(file, project))) return true;
        Document document = fileDocumentManager.getCachedDocument(file);
        CharSequence s = document != null ? document.getCharsSequence() : LoadTextUtil.loadText(file, -1);
        Map<K, V> map = indexer.map(FileContentImpl.createByText(file, s, project));
        for (K key : dataKeys) {
          V value = map.get(key);
          if (value == null) return true;
          if (valueChecker != null && !valueChecker.value(value)) return true;
        }
        return threadProcessor.process(() -> processor.process(file));
      })) {
        return false;
      }
      return threadProcessor.processQueue();
    }
    return null;
  }

  public static Boolean processFilesContainingAllKeys(@NotNull Collection<? extends FileBasedIndex.AllKeysQuery<?, ?>> queries,
                                                      @NotNull GlobalSearchScope scope,
                                                      @NotNull Processor<? super VirtualFile> processor) {
    FileBasedIndex.AllKeysQuery<?, ?> query = ContainerUtil.getFirstItem(queries);
    if (query != null && query.getIndexId() == IdIndex.NAME && Registry.is("indexing.id.over.vfs")) {
      FileBasedIndex.AllKeysQuery<IdIndexEntry, Integer> q = (FileBasedIndex.AllKeysQuery<IdIndexEntry, Integer>)query;
      return processFilesContainingAllKeys(IdIndex.NAME, q.getDataKeys(), scope, q.getValueChecker(), processor);
    }
    return null;
  }

  public static <K, V> Boolean processFilesContainingAnyKey(@NotNull ID<K, V> indexId,
                                                            @NotNull Collection<? extends K> keys,
                                                            @NotNull GlobalSearchScope scope,
                                                            @Nullable IdFilter idFilter,
                                                            @Nullable Condition<? super V> valueChecker,
                                                            @NotNull Processor<? super VirtualFile> processor) {
    if (indexId == FilenameIndex.NAME && Registry.is("indexing.filename.over.vfs")) {
      ensureIdFilterUpToDate();
      IntOpenHashSet ids = new IntOpenHashSet();
      //noinspection unchecked
      FSRecords.processFilesWithNames((Set<String>)keys, id -> {
        if (idFilter != null && !idFilter.containsFileId(id)) return true;
        ids.add(id);
        return true;
      });
      PersistentFS fs = PersistentFS.getInstance();
      IntIterator iterator = ids.iterator();
      while (iterator.hasNext()) {
        VirtualFile file = fs.findFileById(iterator.nextInt());
        if (file == null || !scope.contains(file)) continue;
        //noinspection unchecked
        if (valueChecker != null && !valueChecker.value((V)file.getName())) continue;
        if (!processor.process(file)) return false;
      }
      return true;
    }
    return null;
  }
}
