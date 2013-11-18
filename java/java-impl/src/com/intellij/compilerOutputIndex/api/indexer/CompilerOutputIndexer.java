package com.intellij.compilerOutputIndex.api.indexer;

import com.intellij.compilerOutputIndex.api.fs.CompilerOutputFilesUtil;
import com.intellij.compilerOutputIndex.api.fs.FileVisitorService;
import com.intellij.openapi.compiler.CompilationStatusAdapter;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.asm4.tree.ClassNode;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Dmitry Batkovich
 */
public class CompilerOutputIndexer extends AbstractProjectComponent {
  private final static Logger LOG = Logger.getInstance(CompilerOutputIndexer.class);

  public final static String TITLE = "Compiler output indexer in progress...";

  private final Map<String, CompilerOutputBaseIndex> myIndexTypeQNameToIndex = new HashMap<String, CompilerOutputBaseIndex>();
  private volatile PersistentHashMap<String, Long> myFileTimestampsIndex;
  private volatile PersistentEnumeratorDelegate<String> myFileEnumerator;

  private final Lock myLock = new ReentrantLock();
  private final AtomicBoolean myInProgress = new AtomicBoolean(false);
  @SuppressWarnings("SetReplaceableByEnumSet")
  private final Set<CompilerOutputIndexFeature> myCurrentEnabledFeatures = new ConcurrentHashSet<CompilerOutputIndexFeature>();
  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  public static CompilerOutputIndexer getInstance(final Project project) {
    return project.getComponent(CompilerOutputIndexer.class);
  }

  protected CompilerOutputIndexer(final Project project) {
    super(project);
  }

  private ID<String, Long> getFileTimestampsIndexId() {
    return CompilerOutputIndexUtil.generateIndexId("ProjectCompilerOutputClassFilesTimestamps", myProject);
  }

  @Override
  public final void projectOpened() {
    for (final CompilerOutputIndexFeature feature : CompilerOutputIndexFeature.values()) {
      final RegistryValue registryValue = feature.getRegistryValue();
      registryValue.addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(final RegistryValue rawValue) {
          final Collection<Class<? extends CompilerOutputBaseIndex>> requiredIndexes = feature.getRequiredIndexes();
          if (rawValue.asBoolean()) {
            if (myCurrentEnabledFeatures.add(feature)) {
              if (myCurrentEnabledFeatures.size() == 1) {
                doEnable();
              }
              addIndexes(requiredIndexes);
            }
          }
          else {
            removeIndexes(requiredIndexes);
            myCurrentEnabledFeatures.remove(feature);
          }
        }
      }, myProject);
      if (registryValue.asBoolean()) {
        if (myCurrentEnabledFeatures.add(feature)) {
          if (myCurrentEnabledFeatures.size() == 1) {
            doEnable();
          }
          addIndexes(feature.getRequiredIndexes());
        }
      }
    }
  }

  private CompilerOutputBaseIndex[] getAllIndexes() {
    return Extensions.getExtensions(CompilerOutputBaseIndex.EXTENSION_POINT_NAME, myProject);
  }

  private void addIndexes(final Collection<Class<? extends CompilerOutputBaseIndex>> indexes) {
    final Collection<CompilerOutputBaseIndex> indexesToReindex = new ArrayList<CompilerOutputBaseIndex>();
    for (final Class<? extends CompilerOutputBaseIndex> indexClass : indexes) {
      final String canonicalName = indexClass.getCanonicalName();
      if (!myIndexTypeQNameToIndex.containsKey(canonicalName)) {
        final CompilerOutputBaseIndex index = Extensions.findExtension(CompilerOutputBaseIndex.EXTENSION_POINT_NAME, myProject, indexClass);
        myIndexTypeQNameToIndex.put(canonicalName, index);
        if (index.initIfNeed()) {
          indexesToReindex.add(index);
        }
      }
    }
    if (!indexesToReindex.isEmpty()) {
      if (myInProgress.compareAndSet(false, true)) {
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, TITLE) {
          @Override
          public void onCancel() {
            myInProgress.set(false);
          }

          @Override
          public void onSuccess() {
            myInProgress.set(false);
          }

          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            reindex(new FileVisitorService.ProjectClassFiles(CompilerOutputIndexer.this.myProject), indexesToReindex, true, indicator);
          }
        });
      }
    }
  }

  private void removeIndexes(final Collection<Class<? extends CompilerOutputBaseIndex>> indexes) {
    final Set<Class<? extends CompilerOutputBaseIndex>> toRemove = ContainerUtil.newHashSet(indexes);
    for (final CompilerOutputIndexFeature feature : CompilerOutputIndexFeature.values()) {
      if (feature.getRegistryValue().asBoolean()) {
        for (final Class<? extends CompilerOutputBaseIndex> aClass : feature.getRequiredIndexes()) {
          toRemove.remove(aClass);
        }
      }
    }
    for (final Class aClass : toRemove) {
      myIndexTypeQNameToIndex.remove(aClass.getCanonicalName());
    }
  }

  private void doEnable() {
    if (myInitialized.compareAndSet(false, true)) {
      initTimestampIndex();
      final File storageFile =
        IndexInfrastructure.getStorageFile(CompilerOutputIndexUtil.generateIndexId("compilerOutputIndexFileId.enum", myProject));

      try {
        myFileEnumerator = IOUtil.openCleanOrResetBroken(new ThrowableComputable<PersistentEnumeratorDelegate<String>, IOException>() {
          @Override
          public PersistentEnumeratorDelegate<String> compute() throws IOException {
            return new PersistentEnumeratorDelegate<String>(storageFile, new EnumeratorStringDescriptor(), 2048);
          }
        }, storageFile);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      CompilerManager.getInstance(myProject).addCompilationStatusListener(new CompilationStatusAdapter() {
        @Override
        public void fileGenerated(final String outputRoot, final String relativePath) {
          if (StringUtil.endsWith(relativePath, CompilerOutputFilesUtil.CLASS_FILES_SUFFIX) && !myCurrentEnabledFeatures.isEmpty()) {
            try {
              doIndexing(new File(outputRoot, relativePath), myIndexTypeQNameToIndex.values(), false, null);
            }
            catch (ProcessCanceledException e0) {
              throw e0;
            }
            catch (RuntimeException e) {
              LOG.error(e);
            }
          }
        }
      }, myProject);
    }
  }

  private void initTimestampIndex() {
    final File storageFile = IndexInfrastructure.getStorageFile(getFileTimestampsIndexId());
    try {
      myFileTimestampsIndex = IOUtil.openCleanOrResetBroken(
        new ThrowableComputable<PersistentHashMap<String, Long>, IOException>() {
          @Override
          public PersistentHashMap<String, Long> compute() throws IOException {
            return new PersistentHashMap<String, Long>(storageFile,
                                                       new EnumeratorStringDescriptor(), new DataExternalizer<Long>() {
              @Override
              public void save(final DataOutput out, final Long value) throws IOException {
                out.writeLong(value);
              }

              @Override
              public Long read(final DataInput in) throws IOException {
                return in.readLong();
              }
            });
          }
        },
        new Runnable() {
          public void run() {
            FileUtil.delete(IndexInfrastructure.getIndexRootDir(getFileTimestampsIndexId()));
          }
        }
      );
    } catch (IOException ex) {
      throw new RuntimeException("Timestamps index not initialized", ex);
    }
  }

  public void reindex(final FileVisitorService visitorService, final @NotNull ProgressIndicator indicator) {
    reindex(visitorService, myIndexTypeQNameToIndex.values(), false, indicator);
  }

  private void reindex(final FileVisitorService visitorService,
                       final @NotNull Collection<CompilerOutputBaseIndex> indexes,
                       final boolean force,
                       final @NotNull ProgressIndicator indicator) {
    myLock.lock();
    try {
      indicator.setText(TITLE);
      visitorService.visit(new Consumer<File>() {
        @Override
        public void consume(final File file) {
          try {
            doIndexing(file, indexes, force, indicator);
          }
          catch (ProcessCanceledException e0) {
            throw e0;
          }
          catch (RuntimeException e) {
            LOG.error(e);
          }
        }
      });
    }
    finally {
      myLock.unlock();
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void doIndexing(@NotNull final File file,
                          @NotNull final Collection<CompilerOutputBaseIndex> indexes,
                          final boolean force,
                          @Nullable final ProgressIndicator indicator) {
    final String filePath;
    try {
      filePath = file.getCanonicalPath();
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    final Long timestamp;
    ProgressManager.checkCanceled();
    final long currentTimeStamp = file.lastModified();
    if (force || (timestamp = getTimestamp(filePath)) == null || timestamp != currentTimeStamp) {
      putTimestamp(filePath, currentTimeStamp);
      final ClassNode inputData = new ClassNode(Opcodes.ASM4);
      InputStream is = null;
      try {
        is = new FileInputStream(file);
        final ClassReader reader = new ClassReader(is);
        reader.accept(inputData, ClassReader.EXPAND_FRAMES);
      }
      catch (IOException e) {
        removeTimestamp(filePath);
        return;
      }
      finally {
        if (is != null) {
          try {
            is.close();
          }
          catch (IOException ignored) {
          }
        }
      }
      try {
        if (indicator != null) {
          indicator.setText2(filePath);
        }
        final int id = myFileEnumerator.enumerate(filePath);
        for (final CompilerOutputBaseIndex index : indexes) {
          index.update(id, inputData);
        }
      }
      catch (RuntimeException e) {
        LOG.error(String.format("can't index file: %s", file.getAbsolutePath()), e);
      }
      catch (IOException e) {
        LOG.error(String.format("can't index file: %s", file.getAbsolutePath()), e);
      }
    }
  }

  public void clear() {
    try {
      myFileTimestampsIndex.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    initTimestampIndex();
    for (final CompilerOutputBaseIndex index : getAllIndexes()) {
      index.clearIfInitialized();
    }
  }

  private void removeTimestamp(final String fileId) {
    try {
      myFileTimestampsIndex.remove(fileId);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private Long getTimestamp(final String fileName) {
    try {
      return myFileTimestampsIndex.get(fileName);
    }
    catch (IOException e) {
      LOG.error(e);
      return 0L;
    }
  }

  private void putTimestamp(final String fileName, final long timestamp) {
    try {
      myFileTimestampsIndex.put(fileName, timestamp);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }


  @Override
  public void projectClosed() {
    if (myInitialized.get()) {
      for (final CompilerOutputBaseIndex index : getAllIndexes()) {
        index.closeIfInitialized();
      }
      try {
        myFileTimestampsIndex.close();
        myFileEnumerator.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @TestOnly
  public void removeIndexes() {
    for (final CompilerOutputBaseIndex index : getAllIndexes()) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(index.getIndexId()));
    }
    FileUtil.delete(IndexInfrastructure.getIndexRootDir(getFileTimestampsIndexId()));
  }

  /**
   * try to find index with corresponding class only in currently enabled indexes
   */
  @SuppressWarnings("unchecked")
  public <T extends CompilerOutputBaseIndex> T getIndex(final Class<T> tClass) {
    final CompilerOutputBaseIndex index = myIndexTypeQNameToIndex.get(tClass.getCanonicalName());
    if (index == null) {
      throw new RuntimeException(String.format("index class with name %s not found", tClass.getName()));
    }
    return (T)index;
  }
}
