/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.impl.PersistentIntList;
import com.intellij.psi.impl.file.impl.ResolveScopeManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RefResolveServiceImpl extends RefResolveService implements Runnable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.RefResolveService");
  private final AtomicInteger fileCount = new AtomicInteger();
  private final AtomicLong bytesSize = new AtomicLong();
  private final AtomicLong refCount = new AtomicLong();
  private final PersistentIntList storage;
  private final Deque<VirtualFile> filesToResolve = new ArrayDeque<VirtualFile>();
  private final ConcurrentBitSet fileIsInQueue = new ConcurrentBitSet();
  private final ConcurrentBitSet fileIsResolved;
  private final ApplicationEx myApplication;
  private volatile boolean myDisposed;
  private volatile boolean upToDate;
  private volatile boolean enabled = true;
  private final FileWriter log;
  private final ProjectFileIndex myProjectFileIndex;


  public RefResolveServiceImpl(final Project project,
                               final MessageBus messageBus,
                               final PsiManager psiManager,
                               StartupManager startupManager,
                               ApplicationEx application,
                               ProjectFileIndex projectFileIndex) throws IOException {
    super(project);
    myApplication = application;
    myProjectFileIndex = projectFileIndex;
    if (ResolveScopeManagerImpl.ENABLED_REF_BACK) {
      File indexFile = new File(getStorageDirectory(), "index");
      File dataFile = new File(getStorageDirectory(), "data");
      fileIsResolved = ConcurrentBitSet.readFrom(new File(getStorageDirectory(), "bitSet"));

      final boolean initial = !indexFile.exists() || !dataFile.exists();
      storage = new PersistentIntList(indexFile, dataFile, initial);
      Disposer.register(this, storage);
      if (!application.isUnitTestMode()) {
        startupManager.runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            init(messageBus, psiManager);
          }
        });
      }
      log = new FileWriter(new File(getStorageDirectory(), "log.txt"));
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

  public static List<VirtualFile> toVf(@NotNull int[] ids) {
    List<VirtualFile> res = new ArrayList<VirtualFile>();
    for (int id : ids) {
      VirtualFile file = PersistentFS.getInstance().findFileById(id);
      if (file != null) {
        res.add(file);
      }
    }
    return res;
  }

  public static String toVfString(@NotNull int[] backIds) {
    List<VirtualFile> list = toVf(backIds);
    return toVfString(list);
  }

  private static String toVfString(@NotNull List<VirtualFile> list) {
    List<VirtualFile> sub = list.subList(0, Math.min(list.size(), 100));
    return list.size() + " files: " + StringUtil.join(sub, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile file) {
        return file.getName();
      }
    }, ", ")+(list.size()==sub.size() ? "" : "...");
  }

  private void init(@NotNull MessageBus messageBus, @NotNull PsiManager psiManager) {
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter(){
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        fileCount.set(0);
        List<VirtualFile> files = ContainerUtil.mapNotNull(events, new Function<VFileEvent, VirtualFile>() {
          @Override
          public VirtualFile fun(VFileEvent event) {
            return event.getFile();
          }
        });
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
    myApplication.addApplicationListener(new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        disable();
      }

      @Override
      public void writeActionFinished(Object action) {
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
    Disposer.register(this, HeavyProcessLatch.INSTANCE.addListener(new HeavyProcessLatch.HeavyProcessListener() {
      @Override
      public void processStarted() {
        disable();
      }

      @Override
      public void processFinished() {
        enable();
      }
    }));

    startThread();
  }

  // return true if file was added to queue
  private boolean queueIfNeeded(VirtualFile virtualFile, @NotNull Project project) {
    return toResolve(virtualFile, project) && queueUpdate(virtualFile);
  }

  private boolean toResolve(VirtualFile virtualFile, @NotNull Project project) {
    if (virtualFile != null &&
        virtualFile.isValid() &&
        project.isInitialized() &&
        myProjectFileIndex.isInContent(virtualFile)) {
      if (virtualFile.isDirectory()) return true;
      if (virtualFile.getFileType() == StdFileTypes.JAVA) return true;
      if (virtualFile.getFileType() == StdFileTypes.XML && !ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile)) return true;
    }

    // else mark it as resolved so we will not have to check it again
    if (virtualFile instanceof VirtualFileWithId) {
      int id = getAbsId(virtualFile);
      fileIsResolved.set(id);
    }

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
    try {
      log.write(DateFormat.getDateTimeInstance().format(new Date()) + " "+m+"    ; gap="+storage.gap+"\n");
    }
    catch (IOException e) {
      LOG.error(e);
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
      countAndMarkUnresolved(file, new THashSet<VirtualFile>(), true);
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
    fileIsResolved.writeTo(new File(getStorageDirectory(), "bitSet"));
  }

  @Override
  public void run() {
    while (!myDisposed) {
      boolean isEmpty;
      synchronized (filesToResolve) {
        isEmpty = filesToResolve.isEmpty();
      }
      if (!enabled || isEmpty) {
        try {
          waitForQueue();
        }
        catch (InterruptedException e) {
          break;
        }
        continue;
      }
      upToDate = false;
      final CountDownLatch batchProcessedLatch = new CountDownLatch(1);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          new Task.Backgroundable(myProject, "Resolving files...", true) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
              if (ApplicationManager.getApplication().isDisposed()) return;
              try {
                processBatch(indicator);
              }
              finally {
                batchProcessedLatch.countDown();
              }
            }
          }.queue();
        }
      }, myProject.getDisposed());

      try {
        batchProcessedLatch.await();
      }
      catch (InterruptedException e) {
        break;
      }

      synchronized (filesToResolve) {
        upToDate = filesToResolve.isEmpty();
        log("upToDate = " + upToDate);
      }
      flushLog();
    }
  }

  private void processBatch(@NotNull final ProgressIndicator indicator) {
    Set<VirtualFile> set;
    int queuedSize;
    synchronized (filesToResolve) {
      queuedSize = filesToResolve.size();
      set = new THashSet<VirtualFile>(queuedSize);
      // someone might have cleared this bit to mark file as processed
      for (VirtualFile file : filesToResolve) {
        if (fileIsInQueue.clear(getAbsId(file))) {
          set.add(file);
        }
      }
      filesToResolve.clear();
    }
    final ConcurrentIntObjectMap<int[]> fileToForwardIds = new StripedLockIntObjectConcurrentHashMap<int[]>();
    Set<VirtualFile> files = countAndMarkUnresolved(set, false);
    if (files.isEmpty()) return;
    final int size = files.size();
    final Set<VirtualFile> toProcess = Collections.synchronizedSet(files);
    log("Started to resolve "+ size + " files (was queued "+queuedSize+")");

    indicator.setIndeterminate(false);
    ProgressIndicatorUtils.forceWriteActionPriority(indicator, (Disposable)indicator);
    long start = System.currentTimeMillis();
    Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        double fraction = 1 - toProcess.size() * 1.0 / size;
        indicator.setFraction(fraction);
        try {
          if (file.isDirectory() || !toResolve(file, myProject)) {
            return true;
          }
          int fileId = getAbsId(file);
          int i = size - toProcess.size();
          indicator.setText(i + "/" + size + ": Resolving " + file.getPresentableUrl());
          int[] forwardIds = processFile(file, fileId, indicator);
          if (forwardIds == null) {
            //queueUpdate(file);
            return false;
          }
          toProcess.remove(file);
          fileToForwardIds.put(fileId, forwardIds);
        }
        catch (RuntimeException e) {
          indicator.checkCanceled();
        }
        return true;
      }
    };
    boolean success = true;
    try {
      success = JobLauncher
        .getInstance().invokeConcurrentlyUnderProgress(new ArrayList<VirtualFile>(files), indicator, false, false, processor);
    }
    finally {
      queue(toProcess, "re-added after fail. success=" + success);
      storeIds(fileToForwardIds);

      long end = System.currentTimeMillis();
      log("Resolved batch of " + (size - toProcess.size()) + " from " + size + " files in " + ((end - start) / 1000) + "sec. (Gap: " + storage.gap+")");
    }
  }

  private static int getAbsId(@NotNull VirtualFile file) {
    return Math.abs(((VirtualFileWithId)file).getId());
  }

  @NotNull
  private Set<VirtualFile> countAndMarkUnresolved(@NotNull Collection<VirtualFile> files, boolean inDbOnly) {
    Set<VirtualFile> result = new THashSet<VirtualFile>();
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
          doCountAndMarkUnresolved(file, result);
          return true;
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

  private void doCountAndMarkUnresolved(@NotNull VirtualFile file, @NotNull Set<VirtualFile> result) {
    if (file.isDirectory()) {
      fileIsResolved.set(getAbsId(file));
    }
    else if (toResolve(file, myProject)) {
      result.add(file);
      fileIsResolved.clear(getAbsId(file));
    }
  }

  private void enable() {
    enabled = true;
    wakeUp();
  }

  private void disable() {
    enabled = false;
    wakeUp();
  }

  // returns list of resolved files if updated successfully, or null if write action or dumb mode started
  private int[] processFile(@NotNull final VirtualFile file,
                            int fileId,
                            @NotNull final ProgressIndicator indicator) {
    final TIntHashSet forward;
    try {
      forward = calcForwardRefs(file, indicator);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
    catch (ApplicationUtil.CannotRunReadActionException e) {
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
    logf("  ---- "+file.getPresentableUrl() + " processed. forwardIds: "+ toVfString(forwardIds));
    return forwardIds;
  }

  private void storeIds(@NotNull ConcurrentIntObjectMap<int[]> fileToForwardIds) {
    int forwardSize = 0;
    int backwardSize = 0;
    final TIntObjectHashMap<TIntArrayList> fileToBackwardIds = new TIntObjectHashMap<TIntArrayList>(fileToForwardIds.size());
    for (StripedLockIntObjectConcurrentHashMap.IntEntry<int[]> entry : fileToForwardIds.entries()) {
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
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
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
    if (myProject.isDisposed()) throw new ProcessCanceledException();
    if (fileCount.incrementAndGet() % 100 == 0) {
      PsiManager.getInstance(myProject).dropResolveCaches();
      synchronized (storage) {
        storage.flush();
      }
      try {
        log.flush();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    final TIntHashSet forward = new TIntHashSet();

    final PsiFile psiFile = ApplicationUtil.tryRunReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return PsiManager.getInstance(myProject).findFile(virtualFile);
      }
    });
    final int fileId = getAbsId(virtualFile);
    if (psiFile != null) {
      bytesSize.addAndGet(virtualFile.getLength());
      final Set<PsiElement> resolved = new THashSet<PsiElement>();
      ApplicationUtil.tryRunReadAction(new Runnable() {
        @Override
        public void run() {
          indicator.checkCanceled();

          if (psiFile instanceof PsiJavaFile) {
            psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                resolveReference(reference, indicator, resolved);

                super.visitReferenceElement(reference);
              }
            });
          }
          else if (psiFile instanceof XmlFile) {
            psiFile.accept(new XmlRecursiveElementWalkingVisitor() {
              @Override
              public void visitXmlElement(XmlElement element) {
                for (PsiReference reference : element.getReferences()) {
                  resolveReference(reference, indicator, resolved);
                }
                super.visitXmlElement(element);
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

  private void resolveReference(@NotNull PsiReference reference, @NotNull ProgressIndicator indicator, @NotNull Set<PsiElement> resolved) {
    indicator.checkCanceled();
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
    if (!upToDate) return null;
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
  public boolean queue(@NotNull Collection<VirtualFile> files, Object reason) {
    if (files.isEmpty()) {
      return false;
    }
    boolean queued = false;
    List<VirtualFile> added = new ArrayList<VirtualFile>(files.size());
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
}
