package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.Semaphore;

import java.io.IOException;
import java.io.Reader;

public abstract class ReaderProcess {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ReaderProcess");

  private final Reader myReader;
  private final char[] myBuffer = new char[4096];
  private final Semaphore myReadSemaphore = new Semaphore();
  private boolean isClosed;

  public ReaderProcess(Reader reader) throws IOException {
    myReader = reader;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        doRun();
      }
    }                                                        );
  }

  private void doRun() {
    try {
      while (true) {
        while (!myReader.ready()) {
          synchronized (this) {
            myReadSemaphore.up();
            wait(300);
            if (isClosed) return;
          }
        }
        int read = myReader.read(myBuffer);
        if (read == -1) break;

        onTextAvailable(new String(myBuffer, 0, read));
      }
    }
    catch (IOException e) {
      if (isClosed) return;
      LOG.warn(e);
    }
    catch (InterruptedException e) {
      LOG.warn(e);
    }
  }

  protected abstract void onTextAvailable(String text);

  public void readFully() throws InterruptedException {
    synchronized (this) {
      myReadSemaphore.down();
      notifyAll();
    }
    myReadSemaphore.waitForUnsafe();
  }

  public void close() throws IOException {
    synchronized (this) {
      isClosed = true;
      notifyAll();
    }
    myReader.close();
  }
}
