/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.startup;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.io.IOException;

/**
* @author peter
*/
@SuppressWarnings({"SynchronizeOnThis"})
class FileContentQueue extends ArrayBlockingQueue<FileContent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileContentQueue");
  private static final long SIZE_THRESHOLD = 1024*1024;
  private long myTotalSize;

  public FileContentQueue() {
    super(256);
    myTotalSize = 0;
  }

  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  public void put(VirtualFile file) throws InterruptedException {
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
      catch(ProcessCanceledException e) {
        throw e;
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


  public FileContent take() throws InterruptedException {
    final FileContent result = super.take();

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
