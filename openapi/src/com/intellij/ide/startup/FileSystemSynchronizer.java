package com.intellij.ide.startup;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author max
 */
public class FileSystemSynchronizer {
  private final static Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileSystemSynchronizer");

  private ArrayList<CacheUpdater> myUpdaters = new ArrayList<CacheUpdater>();
  private LinkedHashSet<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();
  private Collection/*<VirtualFile>*/[] myUpdateSets;

  private boolean myIsCancelable = false;

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
    final BlockingQueue<FileContent> contentQueue = new ArrayBlockingQueue<FileContent>(16);

    final Thread contentLoadingThread = new Thread(new Runnable() {
      public void run() {
        try {
          for (VirtualFile file : myFilesToUpdate) {
            FileContent content = new FileContent(file);
            try {
              content.getPhysicalBytes();
            }
            catch (IOException e) {
              content.setEmptyContent();
            }
            contentQueue.put(content);
          }
          contentQueue.put(new FileContent(null));
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
      }
    }, "File Content Loading Thread");
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
}
