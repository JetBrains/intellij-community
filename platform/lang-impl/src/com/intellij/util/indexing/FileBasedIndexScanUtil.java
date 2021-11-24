// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilesScanExecutor;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
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
      Collection<K> names = (Collection<K>)FSRecords.getAllNames();
      return ContainerUtil.process(names, processor);
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
    else if (indexId == TodoIndex.NAME && Registry.is("indexing.todo.over.vfs")) {
      ensureIdFilterUpToDate();
      Project project = scope.getProject();
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      TodoIndex index = new TodoIndex();
      FileBasedIndex.InputFilter inputFilter = index.getInputFilter();
      DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = index.getIndexer();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      if (!FilesScanExecutor.processFilesInScope(scope, false, file -> {
        if (idFilter != null && !idFilter.containsFileId(getFileId(file))) return true;
        if (!FileBasedIndexImpl.acceptsInput(inputFilter, new IndexedFileImpl(file, project))) return true;
        Document document = fileDocumentManager.getCachedDocument(file);
        CharSequence s = document != null ? document.getCharsSequence() : LoadTextUtil.loadText(file, -1);
        Map<TodoIndexEntry, Integer> map = indexer.map(FileContentImpl.createByText(file, s, project));
        //noinspection unchecked
        Collection<K> keys = (Collection<K>)map.keySet();
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
      FSRecords.processFilesWithName((String)dataKey, id -> {
        if (idFilter != null && !idFilter.containsFileId(id)) return true;
        ids.add(id);
        return true;
      });
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      PersistentFS fs = PersistentFS.getInstance();
      IntIterator iterator = ids.iterator();
      while (iterator.hasNext()) {
        VirtualFile file = fs.findFileById(iterator.nextInt());
        if (file == null || !scope.contains(file)) continue;
        if (!threadProcessor.process(() -> processor.process(file, null))) return false;
        if (ensureValueProcessedOnce) break;
      }
      return threadProcessor.processQueue();
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
    else if (indexId == TodoIndex.NAME && Registry.is("indexing.todo.over.vfs")) {
      ensureIdFilterUpToDate();
      Project project = scope.getProject();
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      TodoIndex index = new TodoIndex();
      FileBasedIndex.InputFilter inputFilter = index.getInputFilter();
      DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = index.getIndexer();
      InThisThreadProcessor threadProcessor = new InThisThreadProcessor();
      ConcurrentHashMap<V, Boolean> visitedValues = ensureValueProcessedOnce ? new ConcurrentHashMap<>() : null;
      if (!FilesScanExecutor.processFilesInScope(scope, false, file -> {
        if (idFilter != null && !idFilter.containsFileId(getFileId(file))) return true;
        if (!FileBasedIndexImpl.acceptsInput(inputFilter, new IndexedFileImpl(file, project))) return true;
        Document document = fileDocumentManager.getCachedDocument(file);
        CharSequence s = document != null ? document.getCharsSequence() : LoadTextUtil.loadText(file, -1);
        Map<TodoIndexEntry, Integer> map = indexer.map(FileContentImpl.createByText(file, s, project));
        //noinspection unchecked
        V value = (V)map.get(dataKey);
        if (value == null) return true;
        if (ensureValueProcessedOnce && visitedValues.put(value, true) != null) return true;
        return threadProcessor.process(() -> processor.process(file, value));
      })) return false;
      return threadProcessor.processQueue();
    }
    return null;
  }
}
