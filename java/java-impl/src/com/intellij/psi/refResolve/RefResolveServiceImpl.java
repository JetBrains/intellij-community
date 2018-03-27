/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.refResolve;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBus;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RefResolveServiceImpl extends RefResolveService implements Runnable, Disposable {
  private static final Logger LOG = Logger.getInstance(RefResolveServiceImpl.class);
  private final AtomicInteger fileCount = new AtomicInteger();
  private final AtomicLong bytesSize = new AtomicLong();
  private final AtomicLong refCount = new AtomicLong();
  private final PersistentIntList storage;
  private final Deque<VirtualFile> filesToResolve = new ArrayDeque<>(); // guarded by filesToResolve
  private final ConcurrentBitSet fileIsInQueue = new ConcurrentBitSet();
  private final ConcurrentBitSet fileIsResolved;
  private final ApplicationEx myApplication;
  private volatile boolean myDisposed;
  private volatile boolean upToDate;
  private final AtomicInteger enableVetoes = new AtomicInteger();  // number of disable() calls. To enable the service, there should be at least corresponding number of enable() calls.
  private final FileWriter log;
  private final ProjectFileIndex myProjectFileIndex;


  public RefResolveServiceImpl(final Project project,
                               final MessageBus messageBus,
                               final PsiManager psiManager,
                               StartupManager startupManager,
                               ApplicationEx application,
                               ProjectFileIndex projectFileIndex) throws IOException {
    super(project);
    ((FutureTask)resolveProcess).run();
    myApplication = application;
    myProjectFileIndex = projectFileIndex;
    if (ENABLED) {
      log = new FileWriter(new File(getStorageDirectory(), "log.txt"));

      File dataFile = new File(getStorageDirectory(), "data");
      fileIsResolved = ConcurrentBitSet.readFrom(new File(getStorageDirectory(), "bitSet"));
      log("Read resolved file bitset: " + fileIsResolved);

      int maxId = FSRecords.getMaxId();
      PersistentIntList list = new PersistentIntList(dataFile, dataFile.exists() ? 0 : maxId);
      if (list.getSize() == maxId) {
        storage = list;
      }
      else {
        // just to be safe, re-resolve all if VFS files count changes since last restart
        Disposer.dispose(list);
        storage = new PersistentIntList(dataFile, maxId);
        log("VFS maxId changed: was "+list.getSize()+"; now: "+maxId+"; re-resolving everything");
        fileIsResolved.clear();
      }
      Disposer.register(this, storage);
      if (!application.isUnitTestMode()) {
        startupManager.runWhenProjectIsInitialized(() -> {
          initListeners(messageBus, psiManager);
          startThread();
        });
      }
      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          try {
            save();
            log.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }
    else {
      log = null;
      fileIsResolved = null;
      storage = null;
    }
  }

  @NotNull
  private static List<VirtualFile> toVf(@NotNull int[] ids) {
    List<VirtualFile> res = new ArrayList<>();
    for (int id : ids) {
      VirtualFile file = PersistentFS.getInstance().findFileById(id);
      if (file != null) {
        res.add(file);
      }
    }
    return res;
  }

  @NotNull
  private static String toVfString(@NotNull int[] backIds) {
    List<VirtualFile> list = toVf(backIds);
    return toVfString(list);
  }

  @NotNull
  private static String toVfString(@NotNull Collection<VirtualFile> list) {
    List<VirtualFile> sub = new ArrayList<>(list).subList(0, Math.min(list.size(), 100));
    return list.size() + " files: " + StringUtil.join(sub, file -> file.getName(), ", ") + (list.size() == sub.size() ? "" : "...");
  }

  private void initListeners(@NotNull MessageBus messageBus, @NotNull PsiManager psiManager) {
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        fileCount.set(0);
        List<VirtualFile> files = ContainerUtil.mapNotNull(events, (Function<VFileEvent, VirtualFile>)event -> event.getFile());
        queue(files, "VFS events " + events.size());
      }
    });
    psiManager.addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        PsiFile file = event.getFile();
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
        if (virtualFile != null) {
          queue(Collections.singletonList(virtualFile), event);
        }
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }
    });

    messageBus.connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        disable();
      }

      @Override
      public void exitDumbMode() {
        enable();
      }
    });
    messageBus.connect().subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
      @Override
      public void powerSaveStateChanged() {
        if (PowerSaveMode.isEnabled()) {
          enable();
        }
        else {
          disable();
        }
      }
    });
    myApplication.addApplicationListener(new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(@NotNull Object action) {
        disable();
      }

      @Override
      public void writeActionFinished(@NotNull Object action) {
        enable();
      }

      @Override
      public void applicationExiting() {
        disable();
      }
    }, this);
    VirtualFileManager.getInstance().addVirtualFileManagerListener(new VirtualFileManagerListener() {
      @Override
      public void beforeRefreshStart(boolean asynchronous) {
        disable();
      }

      @Override
      public void afterRefreshFinish(boolean asynchronous) {
        enable();
      }
    }, this);
    HeavyProcessLatch.INSTANCE.addListener(new HeavyProcessLatch.HeavyProcessListener() {
      @Override
      public void processStarted() {
      }

      @Override
      public void processFinished() {
        wakeUp();
      }
    }, this);
  }

  // return true if file was added to queue
  private boolean queueIfNeeded(VirtualFile virtualFile, @NotNull Project project) {
    return toResolve(virtualFile, project) && queueUpdate(virtualFile);
  }

  private boolean toResolve(VirtualFile virtualFile, @NotNull Project project) {
    if (virtualFile != null &&
        virtualFile.isValid() &&
        project.isInitialized() &&
        myProjectFileIndex.isInSourceContent(virtualFile) &&
        isSupportedFileType(virtualFile)) {
      return true;
    }

    // else mark it as resolved so we will not have to check it again
    if (virtualFile instanceof VirtualFileWithId) {
      int id = getAbsId(virtualFile);
      fileIsResolved.set(id);
    }

    return false;
  }

  public static boolean isSupportedFileType(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) return true;
    if (virtualFile.getFileType() == StdFileTypes.JAVA) return true;
    if (virtualFile.getFileType() == StdFileTypes.XML && !ProjectUtil.isProjectOrWorkspaceFile(virtualFile)) return true;
    final String extension = virtualFile.getExtension();
    if ("groovy".equals(extension) || "kt".equals(extension)) return true;
    return false;
  }

  @NotNull
  private File getStorageDirectory() {
    String dirName = myProject.getName() + "."+Integer.toHexString(myProject.getPresentableUrl().hashCode());
    File dir = new File(PathManager.getSystemPath(), "refs/" + dirName);
    FileUtil.createDirectory(dir);
    return dir;
  }


  private void log(String m) {
    //System.out.println(m);
    logf(m);
  }

  private void logf(String m) {
    if (LOG.isDebugEnabled()) {
      try {
        log.write(DateFormat.getDateTimeInstance().format(new Date()) + " "+m+/*"    ; gap="+storage.gap+*/"\n");
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void flushLog() {
    try {
      log.flush();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  // return true if file was added to queue
  private boolean queueUpdate(@NotNull VirtualFile file) {
    synchronized (filesToResolve) {
      if (!(file instanceof VirtualFileWithId)) return false;
      int fileId = getAbsId(file);
      countAndMarkUnresolved(file, new LinkedHashSet<>(), true);
      boolean alreadyAdded = fileIsInQueue.set(fileId);
      if (!alreadyAdded) {
        filesToResolve.add(file);
      }
      upToDate = false;
      wakeUpUnderLock();
      return !alreadyAdded;
    }
  }

  private void wakeUp() {
    synchronized (filesToResolve) {
      wakeUpUnderLock();
    }
  }

  private void wakeUpUnderLock() {
    filesToResolve.notifyAll();
  }

  private void waitForQueue() throws InterruptedException {
    synchronized (filesToResolve) {
      filesToResolve.wait(1000);
    }
  }

  private void startThread() {
    new Thread(this, "Ref resolve service").start();
    upToDate = true;
    queueUnresolvedFilesSinceLastRestart();
  }

  private void queueUnresolvedFilesSinceLastRestart() {
    PersistentFS fs = PersistentFS.getInstance();
    int maxId = FSRecords.getMaxId();
    TIntArrayList list = new TIntArrayList();
    for (int id= fileIsResolved.nextClearBit(1); id >= 0 && id < maxId; id = fileIsResolved.nextClearBit(id + 1)) {
      int nextSetBit = fileIsResolved.nextSetBit(id);
      int endOfRun = Math.min(maxId, nextSetBit == -1 ? maxId : nextSetBit);
      do {
        VirtualFile virtualFile = fs.findFileById(id);
        if (queueIfNeeded(virtualFile, myProject)) {
          list.add(id);
        }
        else {
          fileIsResolved.set(id);
        }
      }
      while (++id < endOfRun);
    }
    log("Initially added to resolve " + toVfString(list.toNativeArray()));
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  private void save() throws IOException {
    log("Saving resolved file bitset: "+fileIsResolved);
    fileIsResolved.writeTo(new File(getStorageDirectory(), "bitSet"));
    log("list.size = " + storage.getSize());
  }

  private volatile Future<?> resolveProcess = new FutureTask<>(EmptyRunnable.getInstance(), null); // write from EDT only

  @Override
  public void run() {
    while (!myDisposed) {
      boolean isEmpty;
      synchronized (filesToResolve) {
        isEmpty = filesToResolve.isEmpty();
      }
      if (enableVetoes.get() > 0 ||
          isEmpty ||
          !resolveProcess.isDone() ||
          HeavyProcessLatch.INSTANCE.isRunning() ||
          PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments()) {
        try {
          waitForQueue();
        }
        catch (InterruptedException e) {
          break;
        }
        continue;
      }
      final Set<VirtualFile> files = pollFilesToResolve();
      if (files.isEmpty()) continue;

      upToDate = false;

      myApplication.invokeLater(() -> {
        if (!resolveProcess.isDone()) return;
        log("Started to resolve " + files.size() + " files");

        Task.Backgroundable backgroundable = new Task.Backgroundable(myProject, "Resolving files...", false) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            if (!myApplication.isDisposed()) {
              processBatch(indicator, files);
            }
          }
        };
        ProgressIndicator indicator;
        if (files.size() > 1) {
          //show progress
          indicator = new BackgroundableProcessIndicator(backgroundable);
        }
        else {
          indicator = new MyProgress();
        }
        resolveProcess = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(backgroundable, indicator, null);
      }, myProject.getDisposed());

      flushLog();
    }
  }

  private volatile int resolvedInPreviousBatch;
  private void processBatch(@NotNull final ProgressIndicator indicator, @NotNull Set<VirtualFile> files) {
    assert !myApplication.isDispatchThread();
    final int resolvedInPreviousBatch = this.resolvedInPreviousBatch;
    final int totalSize = files.size() + resolvedInPreviousBatch;
    final IntObjectMap<int[]> fileToForwardIds = ContainerUtil.createConcurrentIntObjectMap();
    final Set<VirtualFile> toProcess = Collections.synchronizedSet(files);
    indicator.setIndeterminate(false);
    ProgressIndicatorUtils.forceWriteActionPriority(indicator, (Disposable)indicator);
    long start = System.currentTimeMillis();
    Processor<VirtualFile> processor = file -> {
      double fraction = 1 - toProcess.size() * 1.0 / totalSize;
      indicator.setFraction(fraction);
      try {
        if (!file.isDirectory() && toResolve(file, myProject)) {
          int fileId = getAbsId(file);
          int i = totalSize - toProcess.size();
          indicator.setText(i + "/" + totalSize + ": Resolving " + file.getPresentableUrl());
          int[] forwardIds = processFile(file, fileId, indicator);
          if (forwardIds == null) {
            //queueUpdate(file);
            return false;
          }
          fileToForwardIds.put(fileId, forwardIds);
        }
        toProcess.remove(file);
        return true;
      }
      catch (RuntimeException e) {
        indicator.checkCanceled();
      }
      return true;
    };
    boolean success = true;
    try {
      success = processFilesConcurrently(files, indicator, processor);
    }
    finally {
      this.resolvedInPreviousBatch = toProcess.isEmpty() ? 0 : totalSize - toProcess.size();
      queue(toProcess, "re-added after fail. success=" + success);
      storeIds(fileToForwardIds);

      long end = System.currentTimeMillis();
      log("Resolved batch of " + (totalSize - toProcess.size()) + " from " + totalSize + " files in " + ((end - start) / 1000) + "sec. (Gap: " + storage.gap+")");
      synchronized (filesToResolve) {
        upToDate = filesToResolve.isEmpty();
        log("upToDate = " + upToDate);
        if (upToDate) {
          for (Listener listener : myListeners) {
            listener.allFilesResolved();
          }
        }
      }
    }
  }

  private boolean processFilesConcurrently(@NotNull Set<VirtualFile> files,
                                           @NotNull final ProgressIndicator indicator,
                                           @NotNull final Processor<VirtualFile> processor) {
    final List<VirtualFile> fileList = new ArrayList<>(files);
    // fine but grabs all CPUs
    //return JobLauncher.getInstance().invokeConcurrentlyUnderProgress(fileList, indicator, false, false, processor);

    int parallelism = CacheUpdateRunner.indexingThreadCount();
    final Callable<Boolean> processFileFromSet = () -> {
      final boolean[] result = {true};
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        while (true) {
          ProgressManager.checkCanceled();
          VirtualFile file;
          synchronized (fileList) {
            file = fileList.isEmpty() ? null : fileList.remove(fileList.size() - 1);
          }
          if (file == null) {
            break;
          }
          if (!processor.process(file)) {
            result[0] = false;
            break;
          }
        }
      }, indicator);
      return result[0];
    };
    List<Future<Boolean>> futures = ContainerUtil.map(Collections.nCopies(parallelism, ""), s -> myApplication.executeOnPooledThread(processFileFromSet));

    List<Boolean> results = ContainerUtil.map(futures, future -> {
      try {
        return future.get();
      }
      catch (Exception e) {
        LOG.error(e);
      }
      return false;
    });

    return !ContainerUtil.exists(results, result -> {
      return result != null && !result;  // null means PCE
    });
  }

  @NotNull
  private Set<VirtualFile> pollFilesToResolve() {
    Set<VirtualFile> set;
    synchronized (filesToResolve) {
      int queuedSize = filesToResolve.size();
      set = new LinkedHashSet<>(queuedSize);
      // someone might have cleared this bit to mark file as processed
      for (VirtualFile file : filesToResolve) {
        if (fileIsInQueue.clear(getAbsId(file))) {
          set.add(file);
        }
      }
      filesToResolve.clear();
    }
    return countAndMarkUnresolved(set, false);
  }

  private static int getAbsId(@NotNull VirtualFile file) {
    return Math.abs(((VirtualFileWithId)file).getId());
  }

  @NotNull
  private Set<VirtualFile> countAndMarkUnresolved(@NotNull Collection<VirtualFile> files, boolean inDbOnly) {
    Set<VirtualFile> result = new LinkedHashSet<>();
    for (VirtualFile file : files) {
      countAndMarkUnresolved(file, result, inDbOnly);
    }
    return result;
  }

  private void countAndMarkUnresolved(@NotNull VirtualFile file, @NotNull final Set<VirtualFile> result, final boolean inDbOnly) {
    if (file.isDirectory()) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          return doCountAndMarkUnresolved(file, result);
        }

        @Nullable
        @Override
        public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
          return inDbOnly ? ((NewVirtualFile)file).iterInDbChildren() : null;
        }
      });
    }
    else {
      doCountAndMarkUnresolved(file, result);
    }
  }

  // return true if continue to process sub-directories of the {@code file}, false if the file is already processed
  private boolean doCountAndMarkUnresolved(@NotNull VirtualFile file, @NotNull Set<VirtualFile> result) {
    if (file.isDirectory()) {
      fileIsResolved.set(getAbsId(file));
      return result.add(file);
    }
    if (toResolve(file, myProject)) {
      result.add(file);
      fileIsResolved.clear(getAbsId(file));
    }
    return true;
  }

  private void enable() {
    // decrement but only if it's positive
    int vetoes;
    do {
      vetoes = enableVetoes.get();
      if (vetoes == 0) break;
    } while(!enableVetoes.compareAndSet(vetoes, vetoes-1));
    wakeUp();
  }

  private void disable() {
    enableVetoes.incrementAndGet();
    wakeUp();
  }

  // returns list of resolved files if updated successfully, or null if write action or dumb mode started
  private int[] processFile(@NotNull final VirtualFile file, int fileId, @NotNull final ProgressIndicator indicator) {
    final TIntHashSet forward;
    try {
      forward = calcForwardRefs(file, indicator);
    }
    catch (IndexNotReadyException | ApplicationUtil.CannotRunReadActionException e) {
      return null;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      log(ExceptionUtil.getThrowableText(e));
      flushLog();
      return null;
    }

    int[] forwardIds = forward.toArray();
    fileIsResolved.set(fileId);
    logf("  ---- " + file.getPresentableUrl() + " processed. forwardIds: " + toVfString(forwardIds));
    for (Listener listener : myListeners) {
      listener.fileResolved(file);
    }
    return forwardIds;
  }

  private void storeIds(@NotNull IntObjectMap<int[]> fileToForwardIds) {
    int forwardSize = 0;
    int backwardSize = 0;
    final TIntObjectHashMap<TIntArrayList> fileToBackwardIds = new TIntObjectHashMap<>(fileToForwardIds.size());
    for (IntObjectMap.Entry<int[]> entry : fileToForwardIds.entries()) {
      int fileId = entry.getKey();
      int[] forwardIds = entry.getValue();
      forwardSize += forwardIds.length;
      for (int forwardId : forwardIds) {
        TIntArrayList backIds = fileToBackwardIds.get(forwardId);
        if (backIds == null) {
          backIds = new TIntArrayList();
          fileToBackwardIds.put(forwardId, backIds);
        }
        backIds.add(fileId);
        backwardSize++;
      }
    }
    log("backwardSize = " + backwardSize);
    log("forwardSize = " + forwardSize);
    log("fileToForwardIds.size() = "+fileToForwardIds.size());
    log("fileToBackwardIds.size() = "+fileToBackwardIds.size());
    assert forwardSize == backwardSize;

    // wrap in read action so that sudden quit (in write action) would not interrupt us
    myApplication.runReadAction(() -> {
      if (!myApplication.isDisposed()) {
        fileToBackwardIds.forEachEntry(new TIntObjectProcedure<TIntArrayList>() {
          @Override
          public boolean execute(int fileId, TIntArrayList backIds) {
            storage.addAll(fileId, backIds.toNativeArray());
            return true;
          }
        });
      }
    });
  }


  @NotNull
  private TIntHashSet calcForwardRefs(@NotNull final VirtualFile virtualFile, @NotNull final ProgressIndicator indicator)
    throws IndexNotReadyException, ApplicationUtil.CannotRunReadActionException {

    final TIntHashSet forward = new TIntHashSet();

    final PsiFile psiFile = ApplicationUtil.tryRunReadAction(() -> {
      if (myProject.isDisposed()) throw new ProcessCanceledException();
      if (fileCount.incrementAndGet() % 100 == 0) {
        PsiManager.getInstance(myProject).dropResolveCaches();
        try {
          storage.flush();
          log.flush();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      return PsiManager.getInstance(myProject).findFile(virtualFile);
    });
    final int fileId = getAbsId(virtualFile);
    if (psiFile != null) {
      bytesSize.addAndGet(virtualFile.getLength());
      final Set<PsiElement> resolved = new THashSet<>();
      ApplicationUtil.tryRunReadAction(new Runnable() {
        @Override
        public void run() {
          indicator.checkCanceled();

          if (psiFile instanceof PsiJavaFile) {
            psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                indicator.checkCanceled();
                resolveReference(reference, resolved);

                super.visitReferenceElement(reference);
              }
            });
          }
          else {
            psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
              @Override
              public void visitElement(PsiElement element) {
                for (PsiReference reference : element.getReferences()) {
                  indicator.checkCanceled();
                  resolveReference(reference, resolved);
                }
                super.visitElement(element);
              }
            });
          }

          indicator.checkCanceled();
          for (PsiElement element : resolved) {
            PsiFile file = element.getContainingFile();
            addIdAndSuperClasses(file, forward);
          }
        }
      });
    }

    forward.remove(fileId);
    return forward;
  }

  private void resolveReference(@NotNull PsiReference reference, @NotNull Set<PsiElement> resolved) {
    PsiElement element = reference.resolve();
    if (element != null) {
      resolved.add(element);
    }
    refCount.incrementAndGet();
  }

  private static void addIdAndSuperClasses(PsiFile file, @NotNull TIntHashSet forward) {
    if (file instanceof PsiJavaFile && file.getName().equals("Object.class") && ((PsiJavaFile)file).getPackageName().equals("java.lang")) {
      return;
    }
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    if (virtualFile instanceof VirtualFileWithId && forward.add(getAbsId(virtualFile)) && file instanceof PsiClassOwner) {
      for (PsiClass aClass : ((PsiClassOwner)file).getClasses()) {
        for (PsiClass superClass : aClass.getSupers()) {
          addIdAndSuperClasses(superClass.getContainingFile(), forward);
        }
      }
    }
  }

  @Override
  @Nullable
  public int[] getBackwardIds(@NotNull VirtualFileWithId file) {
    if (!isUpToDate()) return null;
    int fileId = getAbsId((VirtualFile)file);
    return storage.get(fileId);
  }

  private String prevLog = "";
  private static final Set<JavaSourceRootType> SOURCE_ROOTS = ContainerUtil.newTroveSet(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE);

  @NotNull
  @Override
  public GlobalSearchScope restrictByBackwardIds(@NotNull final VirtualFile virtualFile, @NotNull GlobalSearchScope scope) {
    final int[] backIds = RefResolveService.getInstance(myProject).getBackwardIds((VirtualFileWithId)virtualFile);
    if (backIds == null) {
      return scope;
    }
    String files = toVfString(backIds);
    String log = "Restricting scope of " + virtualFile.getName() + " to " + files;
    if (!log.equals(prevLog)) {
      log(log);
      flushLog();
      prevLog = log;
    }
    GlobalSearchScope restrictedByBackwardIds = new GlobalSearchScope() {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (!(file instanceof VirtualFileWithId)
            || file.equals(virtualFile)
            || ArrayUtil.indexOf(backIds, getAbsId(file)) != -1) return true;
        return false & !myProjectFileIndex.isUnderSourceRootOfType(file, SOURCE_ROOTS); // filter out source file which we know for sure does not reference the element
      }

      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        return 0;
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean isSearchInLibraries() {
        return false;
      }
    };
    return scope.intersectWith(restrictedByBackwardIds);
  }

  @Override
  public boolean queue(@NotNull Collection<VirtualFile> files, @NotNull Object reason) {
    if (files.isEmpty()) {
      return false;
    }
    boolean queued = false;
    List<VirtualFile> added = new ArrayList<>(files.size());
    for (VirtualFile file : files) {
      boolean wasAdded = queueIfNeeded(file, myProject);
      if (wasAdded) {
        added.add(file);
      }
      queued |= wasAdded;
    }
    if (queued) {
      log("Queued to resolve (from " + reason + "): " + toVfString(added));
      flushLog();
    }
    return queued;
  }

  @Override
  public boolean isUpToDate() {
    return ENABLED && !myDisposed && upToDate;
  }

  @Override
  public int getQueueSize() {
    synchronized (filesToResolve) {
      return filesToResolve.size();
    }
  }

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @Override
  public void addListener(@NotNull Disposable parent, @NotNull final Listener listener) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  private static class MyProgress extends ProgressIndicatorBase implements Disposable{
    @Override
    public void dispose() {
    }
  }
}
