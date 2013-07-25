package com.intellij.compilerOutputIndex.api.indexer;

import com.intellij.compilerOutputIndex.api.fs.CompilerOutputFilesUtil;
import com.intellij.compilerOutputIndex.api.fs.FileVisitorService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumeratorDelegate;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.asm4.ClassReader;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Dmitry Batkovich
 */
public class CompilerOutputIndexer extends AbstractProjectComponent {
  private final static Logger LOG = Logger.getInstance(CompilerOutputIndexer.class);

  public final static String REGISTRY_KEY = "completion.enable.relevant.method.chain.suggestions";
  public final static String TITLE = "Compiler output indexer in progress...";

  private volatile CompilerOutputBaseIndex[] myIndexes;
  private volatile Map<String, CompilerOutputBaseIndex> myIndexTypeQNameToIndex;
  private volatile PersistentHashMap<String, Long> myFileTimestampsIndex;
  private volatile PersistentEnumeratorDelegate<String> myFileEnumerator;
  private volatile boolean myInitialized = false;

  private final Lock myLock = new ReentrantLock();
  private final AtomicBoolean myInProgress = new AtomicBoolean(false);
  private AtomicBoolean myEnabled = new AtomicBoolean(false);

  public static CompilerOutputIndexer getInstance(final Project project) {
    return project.getComponent(CompilerOutputIndexer.class);
  }

  protected CompilerOutputIndexer(final Project project) {
    super(project);
  }

  public boolean isEnabled() {
    return myEnabled.get();
  }

  private ID<String, Long> getFileTimestampsIndexId() {
    return CompilerOutputIndexUtil.generateIndexId("ProjectCompilerOutputClassFilesTimestamps", myProject);
  }

  @Override
  public final void projectOpened() {
    Registry.get(REGISTRY_KEY).addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(final RegistryValue value) {
        myEnabled.set(value.asBoolean());
        if (myEnabled.get()) {
          doEnable();
        }
      }
    }, myProject);

    myEnabled = new AtomicBoolean(Registry.is(REGISTRY_KEY) || ApplicationManager.getApplication().isUnitTestMode());
    if (myEnabled.get()) {
      doEnable();
    }
  }

  private void doEnable() {
    if (!myInitialized) {
      myIndexes = Extensions.getExtensions(CompilerOutputBaseIndex.EXTENSION_POINT_NAME, myProject);
      myIndexTypeQNameToIndex = new HashMap<String, CompilerOutputBaseIndex>();
      boolean needReindex = false;
      for (final CompilerOutputBaseIndex index : myIndexes) {
        if (index.init(myProject)) {
          needReindex = true;
        }
        myIndexTypeQNameToIndex.put(index.getClass().getCanonicalName(), index);
      }
      initTimestampIndex(needReindex);
      try {
        myFileEnumerator = new PersistentEnumeratorDelegate<String>(
          IndexInfrastructure.getStorageFile(CompilerOutputIndexUtil.generateIndexId("compilerOutputIndexFileId.enum", myProject)),
          new EnumeratorStringDescriptor(), 2048);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      CompilerManager.getInstance(myProject).addAfterTask(new CompileTask() {
        @Override
        public boolean execute(final CompileContext context) {
          if (myEnabled.get() && myInProgress.compareAndSet(false, true)) {
            myLock.lock();
            try {
              context.getProgressIndicator().setText("Compiler output indexing in progress");
              final Consumer<File> fileConsumer = new Consumer<File>() {
                @Override
                public void consume(final File file) {
                  try {
                    doIndexing(file, context.getProgressIndicator());
                  }
                  catch (ProcessCanceledException e0) {
                    throw e0;
                  }
                  catch (RuntimeException e) {
                    LOG.error(e);
                  }
                }
              };
              for (final Module module : context.getCompileScope().getAffectedModules()) {
                CompilerOutputFilesUtil.iterateModuleClassFiles(module, fileConsumer);
              }
            }
            finally {
              myLock.unlock();
              myInProgress.set(false);
            }
          }
          return true;
        }
      });
      if (needReindex) {
        reindexAllProjectInBackground();
      }
      myInitialized = true;
    }
  }

  private void initTimestampIndex(final boolean needReindex) {
    if (needReindex) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(getFileTimestampsIndexId()));
    }
    for (int attempts = 0; attempts < 2; attempts++) {
      try {
        myFileTimestampsIndex = new PersistentHashMap<String, Long>(IndexInfrastructure.getStorageFile(getFileTimestampsIndexId()),
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
      catch (IOException e) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(getFileTimestampsIndexId()));
      }
      if (myFileTimestampsIndex != null) {
        return;
      }
    }
    throw new RuntimeException("Timestamps index not initialized");
  }


  public void reindex(final FileVisitorService visitorService, @NotNull final ProgressIndicator indicator) {
    myLock.lock();
    try {
      indicator.setText(TITLE);
      visitorService.visit(new Consumer<File>() {
        @Override
        public void consume(final File file) {
          try {
            doIndexing(file, indicator);
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

  public void reindexAllProjectInBackground() {
    if (myInProgress.compareAndSet(false, true)) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, TITLE) {

        @Override
        public void onCancel() {
          myIndexTypeQNameToIndex.clear();
          myInProgress.set(false);
        }

        @Override
        public void onSuccess() {
          myInProgress.set(false);
        }

        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          reindexAllProject(indicator);
        }
      });
    }
  }

  public void reindexAllProject(@NotNull final ProgressIndicator indicator) {
    reindex(new FileVisitorService.ProjectClassFiles(myProject), indicator);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void doIndexing(@NotNull final File file, @NotNull final ProgressIndicator indicator) {
    final String filePath;
    try {
      filePath = file.getCanonicalPath();
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    final Long timestamp = getTimestamp(filePath);
    ProgressManager.checkCanceled();
    final long currentTimeStamp = file.lastModified();
    if (timestamp == null || timestamp != currentTimeStamp) {
      putTimestamp(filePath, currentTimeStamp);
      final ClassReader reader;
      InputStream is = null;
      try {
        is = new FileInputStream(file);
        reader = new ClassReader(is);
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
        indicator.setText2(filePath);
        final int id = myFileEnumerator.enumerate(filePath);
        for (final CompilerOutputBaseIndex index : myIndexes) {
          index.update(id, reader);
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
    initTimestampIndex(true);
    for (final CompilerOutputBaseIndex index : myIndexes) {
      index.clear();
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
    if (myInitialized) {
      for (final CompilerOutputBaseIndex index : myIndexes) {
        index.projectClosed();
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
    for (final CompilerOutputBaseIndex index : myIndexes) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(index.getIndexId()));
    }
    FileUtil.delete(IndexInfrastructure.getIndexRootDir(getFileTimestampsIndexId()));
  }

  @SuppressWarnings("unchecked")
  public <T extends CompilerOutputBaseIndex> T getIndex(final Class<T> tClass) {
    final CompilerOutputBaseIndex index = myIndexTypeQNameToIndex.get(tClass.getCanonicalName());
    if (index == null) {
      throw new RuntimeException(String.format("index class with name %s not found", tClass.getName()));
    }
    return (T)index;
  }
}
