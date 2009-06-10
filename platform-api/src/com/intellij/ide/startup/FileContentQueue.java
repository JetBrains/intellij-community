/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.startup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;

/**
* @author peter
*/
@SuppressWarnings({"SynchronizeOnThis"})
public class FileContentQueue extends ArrayBlockingQueue<FileContent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileContentQueue");
  private static final long SIZE_THRESHOLD = 1024*1024;
  private long myTotalSize;

  public FileContentQueue() {
    super(256);
    myTotalSize = 0;
  }

  public void queue(final Collection<VirtualFile> files, @Nullable final ProgressIndicator indicator) {
    final Runnable contentLoadingRunnable = new Runnable() {
      public void run() {
        try {
          for (VirtualFile file : files) {
            if (indicator != null) {
              indicator.checkCanceled();
            }

            addLast(file);
          }
        }
        catch (ProcessCanceledException e) {
          // Do nothing, exit the thread.
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        finally {
          try {
            put(new FileContent(null));
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
        }
      }
    };

    ApplicationManager.getApplication().executeOnPooledThread(contentLoadingRunnable);
  }

  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  protected void addLast(VirtualFile file) throws InterruptedException {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    FileContent content = new FileContent(file);

    if (file.isValid()) {
      try {
        final long contentLength = content.getLength();
        if (contentLength < SIZE_THRESHOLD) {
          synchronized (this) {
            while (myTotalSize > SIZE_THRESHOLD) {
              if (indicator != null) indicator.checkCanceled();
              wait(300);
            }
            myTotalSize += contentLength;
          }

          content.getBytes(); // Reads the content bytes and caches them.
        }
      }
      catch (IOException e) {
        content.setEmptyContent();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (InterruptedException e) {
        return;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    else {
      content.setEmptyContent();
    }

    put(content);
  }

  public FileContent take() {
    final FileContent result;
    try {
      result = super.take();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    synchronized (this) {
      try {
        final VirtualFile file = result.getVirtualFile();
        if (file == null || !file.isValid() || result.getLength() >= SIZE_THRESHOLD) return result;
        myTotalSize -= result.getLength();
      }
      finally {
        notifyAll();
      }
    }

    return result;
  }
}
