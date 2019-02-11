// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.actions;

import com.google.common.hash.HashCode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.FileContentHashing;
import com.intellij.psi.stubs.HashCodeDescriptor;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class DumpIndicesAction extends AnAction {
  public DumpIndicesAction() {
    super("Dump indices");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    VirtualFile dir = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
    if (dir == null) return;
    PsiManager psiManager = PsiManager.getInstance(project);

    ProgressManager.getInstance().run(new Task.Modal(project, "Dumping Indices...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        FileContentHashing fileContentHashing = new FileContentHashing();
        try (IndexStorages storages = new IndexStorages(dir.getCanonicalPath())) {
          Ref<IOException> exceptionRef = Ref.create();
          FileBasedIndex.getInstance().iterateIndexableFiles(f -> {
            try {
              ReadAction.run(() -> {
                if (f.isDirectory()) return;
                FileContentImpl content = new FileContentImpl(f, f.contentsToByteArray());
                PsiFile psiFile = psiManager.findFile(f);
                if (psiFile != null) {
                  content.putUserData(IndexingDataKeys.PSI_FILE, psiFile);
                }
                content.putUserData(IndexingDataKeys.PROJECT, project);
                HashCode hashCode = fileContentHashing.hashString(content);
                FileType type = content.getFileType();
                storages.indexFile(content, f, type, hashCode);
              });
            }
            catch (IOException ex) {
              exceptionRef.set(ex);
            }
            return true;
          }, project, indicator);
        }
        catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    });

  }

  private static <K, V> PersistentHashMap<HashCode, Map<K, V>> createStorageForIndex(@NotNull FileBasedIndexExtension<K, V> extension, @NotNull String basePath) throws IOException {
    return new PersistentHashMap<>(new File(basePath, extension.getName().getName()),
                                   HashCodeDescriptor.instance,
                                   new MapDataExternalizer<>(extension.getKeyDescriptor(),
                                                             extension.getValueExternalizer()));
  }

  private static class IndexStorages implements AutoCloseable {
    private final PersistentHashMap<HashCode, Map<?, ?>>[] myStorages;
    private final FileBasedIndexExtension<?, ?>[] myExtensions;
    private final Set<FileType>[] myAcceptedFileTypes;

    IndexStorages(@NotNull String basePath) throws IOException {
      FileBasedIndexExtension[] extensions = FileBasedIndexExtension
        .EXTENSION_POINT_NAME
        .extensions()
        .filter(ex -> !ex.indexDirectories() && ex.dependsOnFileContent() && !ex.getName().equals(StubUpdatingIndex.INDEX_ID))
        .toArray(FileBasedIndexExtension[]::new);
      //noinspection unchecked
      myStorages = new PersistentHashMap[extensions.length];
      //noinspection unchecked
      myAcceptedFileTypes = new Set[extensions.length];
      int i;
      try {
        for (i = 0; i < myStorages.length; i++) {
          FileBasedIndexExtension extension = extensions[i];
          //noinspection unchecked
          myStorages[i] = createStorageForIndex(extension, basePath);
          if (extension.getInputFilter() instanceof FileBasedIndex.FileTypeSpecificInputFilter) {
            THashSet<FileType> acceptedFileTypes = new THashSet<>();
            ((FileBasedIndex.FileTypeSpecificInputFilter)extension.getInputFilter()).registerFileTypesUsedForIndexing(acceptedFileTypes::add);
            myAcceptedFileTypes[i] = acceptedFileTypes;
          }
        }
      }
      catch (IOException e) {
        close();
        throw e;
      }
      myExtensions = extensions;
    }

    void indexFile(FileContent fc, VirtualFile file, FileType type, HashCode hashCode) throws IOException {
      IOException e = null;
      for (int i = 0; i < myExtensions.length; i++) {
        if (myAcceptedFileTypes[i] != null && !myAcceptedFileTypes[i].contains(type)) {
          continue;
        }
        if (!myExtensions[i].getInputFilter().acceptInput(file)) {
          continue;
        }
        Map<?, ?> result = myExtensions[i].getIndexer().map(fc);
        try {
          myStorages[i].put(hashCode, result);
        }
        catch (IOException e1) {
          e = e1;
        }
      }
      if (e != null) {
        throw e;
      }
    }

    public void close() {
      IOException e = null;
      for (PersistentHashMap<HashCode, Map<?, ?>> storage : myStorages) {
        if (storage != null) {
          try {
            storage.close();
          }
          catch (IOException e1) {
            e = e1;
          }
        }
      }
      if (e != null) {
        e.printStackTrace();
      }
    }
  }

  private static class MapDataExternalizer<K, V> implements DataExternalizer<Map<K, V>> {
    private final DataExternalizer<K> myKeyExternalizer;
    private final DataExternalizer<V> myValueExternalizer;

    MapDataExternalizer(DataExternalizer<K> externalizer, DataExternalizer<V> valueExternalizer) {
      myKeyExternalizer = externalizer;
      myValueExternalizer = valueExternalizer;
    }

    @Override
    public void save(@NotNull DataOutput out, Map<K, V> value) throws IOException {
      DataInputOutputUtil.writeSeq(out, value.entrySet(), e -> {
        myKeyExternalizer.save(out, e.getKey());
        myValueExternalizer.save(out, e.getValue());
      });
    }

    @Override
    public Map<K, V> read(@NotNull DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      THashMap<K, V> map = new THashMap<>(size);
      for (int i = 0; i < size; i++) {
        map.put(myKeyExternalizer.read(in), myValueExternalizer.read(in));
      }
      return map;
    }
  }
}
