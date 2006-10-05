package com.intellij.ide.startup;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author max
 */
public class FileSystemSynchronizer {
  private final static Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileSystemSynchronizer");

  private ArrayList<CacheUpdater> myUpdaters = new ArrayList<CacheUpdater>();
  private LinkedHashSet<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();
  private Collection/*<VirtualFile>*/[] myUpdateSets;

  private boolean myIsCancelable = false;
  @NonNls private static final String LOAD_FILES_THREAD_NAME = "File Content Loading Thread";

  public void registerCacheUpdater(@NotNull CacheUpdater cacheUpdater) {
    myUpdaters.add(cacheUpdater);
  }

  public void setCancelable(boolean isCancelable) {
    myIsCancelable = isCancelable;
  }

  public void execute() {
    /*
    long time1 = System.currentTimeMillis();
    */

    if (!myIsCancelable) {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.startNonCancelableSection();
      }
    }

    try {
      if (myUpdateSets == null) { // collectFilesToUpdate() was not executed before
        if (collectFilesToUpdate() == 0) return;
      }

      updateFiles();
    }
    catch (ProcessCanceledException e) {
      for (CacheUpdater updater : myUpdaters) {
        if (updater != null) {
          updater.canceled();
        }
      }
      throw e;
    }
    finally {
      if (!myIsCancelable) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.finishNonCancelableSection();
        }
      }
    }

    /*
    long time2 = System.currentTimeMillis();
    System.out.println("synchronizer.execute() in " + (time2 - time1) + " ms");
    */
  }

  public int collectFilesToUpdate() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText(IdeBundle.message("progress.scanning.files"));
    }

    myUpdateSets = new Collection[myUpdaters.size()];
    for (int i = 0; i < myUpdaters.size(); i++) {
      CacheUpdater updater = myUpdaters.get(i);
      try {
        VirtualFile[] updaterFiles = updater.queryNeededFiles();
        Collection<VirtualFile> localSet = new LinkedHashSet<VirtualFile>(Arrays.asList(updaterFiles));
        myFilesToUpdate.addAll(localSet);
        myUpdateSets[i] = localSet;
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        myUpdateSets[i] = new ArrayList();
      }
    }

    if (indicator != null) {
      indicator.popState();
    }

    if (myFilesToUpdate.size() == 0) {
      updatingDone();
    }

    return myFilesToUpdate.size();
  }

  private void updateFiles() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText(IdeBundle.message("progress.parsing.files"));
    }

    int totalFiles = myFilesToUpdate.size();
    int count = 0;
    final MyContentQueue contentQueue = new MyContentQueue();

    final Thread contentLoadingThread = new Thread(new Runnable() {
      public void run() {
        try {
          for (VirtualFile file : myFilesToUpdate) {
            contentQueue.put(file);
          }
          contentQueue.put(new FileContent(null));
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
      }
    }, LOAD_FILES_THREAD_NAME);
    contentLoadingThread.setPriority(Thread.currentThread().getPriority());
    contentLoadingThread.start();

    while (true) {
      FileContent content = null;
      try {
        content = contentQueue.take();
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      if (content == null) break;
      final VirtualFile file = content.getVirtualFile();
      if (file == null) break;
      if (indicator != null) {
        indicator.setFraction(((double)++count) / totalFiles);
        indicator.setText2(file.getPresentableUrl());
      }
      for (int i = 0; i < myUpdaters.size(); i++) {
        CacheUpdater updater = myUpdaters.get(i);
        if (myUpdateSets[i].remove(file)) {
          try {
            updater.processFile(content);
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Exception e) {
            LOG.error(e);
          }
          if (myUpdateSets[i].isEmpty()) {
            try {
              updater.updatingDone();
            }
            catch (ProcessCanceledException e) {
              throw e;
            }
            catch (Exception e) {
              LOG.error(e);
            }
            myUpdaters.set(i, null);
          }
        }
      }
    }

    updatingDone();

    if (indicator != null) {
      indicator.popState();
    }
  }

  private void updatingDone() {
    for (CacheUpdater updater : myUpdaters) {
      try {
        if (updater != null) updater.updatingDone();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    dropUpdaters();
  }

  private void dropUpdaters() {
    myUpdaters.clear();
    myFilesToUpdate.clear();
    myUpdateSets = null;
  }

  @SuppressWarnings({"SynchronizeOnThis"})
  private static class MyContentQueue extends ArrayBlockingQueue<FileContent> {
    private long totalSize;
    private static final long SIZE_THRESHOLD = 1024*1024;

    public MyContentQueue() {
      super(256);
      totalSize = 0;
    }

    @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
    public void put(VirtualFile file) throws InterruptedException {
      FileContent content;
      synchronized (this) {
        content = new FileContent(file);
          try {
            if (content.getPhysicalLength() < SIZE_THRESHOLD) {
              while (totalSize > SIZE_THRESHOLD) {
                wait();
              }

              totalSize += content.getPhysicalBytes().length;
            }
          }
          catch (IOException e) {
            content.setEmptyContent();
          }
      }

      put(content);
    }


    public FileContent take() throws InterruptedException {
      final FileContent result = super.take();

      synchronized (this) {
        try {
          if (result.getVirtualFile() == null || result.getPhysicalLength() >= SIZE_THRESHOLD) return result;
          totalSize -= result.getPhysicalBytes().length;
        }
        catch (IOException e) {
          LOG.error(e);
        }
        finally {
          notifyAll();
        }
      }

      return result;
    }
  }
}
