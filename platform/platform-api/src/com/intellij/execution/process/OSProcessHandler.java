/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.*;

public class OSProcessHandler extends ProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandler");
  private static final ReaderThread ourReaderThread;
  private final Process myProcess;
  private final String myCommandLine;

  private final ProcessWaitFor myWaitFor;

  private static class ExecutorServiceHolder {
    private static ExecutorService ourThreadExecutorsService = createServiceImpl();

    static ThreadPoolExecutor createServiceImpl() {
      return new ThreadPoolExecutor(10, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        public Thread newThread(Runnable r) {
          return new Thread(r, "OSProcessHandler pooled thread");
        }
      });
    }
  }

  static {
    ourReaderThread = new ReaderThread();
    ourReaderThread.setDaemon(true);
    ourReaderThread.setName("OSProcessHandler streams reader");
    ourReaderThread.start();
  }

  /**
  /**
   * Override this method in order to execute the task with a custom pool
   *
   * @param task a task to run
   */
  protected Future<?> executeOnPooledThread(Runnable task) {
    final Application application = ApplicationManager.getApplication();

    if (application != null) {
      return application.executeOnPooledThread(task);
    }

    if (ExecutorServiceHolder.ourThreadExecutorsService.isShutdown()) { // in tests: the service might be shut down by a previous test
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ExecutorServiceHolder.ourThreadExecutorsService = ExecutorServiceHolder.createServiceImpl();
    }
    return ExecutorServiceHolder.ourThreadExecutorsService.submit(task);
  }

  public OSProcessHandler(final Process process, final String commandLine) {
    myProcess = process;
    myCommandLine = commandLine;
    myWaitFor = new ProcessWaitFor(process);
  }

  private class ProcessWaitFor  {
    private final Future<?> myWaitForThreadFuture;
    private final BlockingQueue<Consumer<Integer>> myTerminationCallback = new ArrayBlockingQueue<Consumer<Integer>>(1);

    public void detach() {
      myWaitForThreadFuture.cancel(true);
    }


    public ProcessWaitFor(final Process process) {
      myWaitForThreadFuture = executeOnPooledThread(new Runnable() {
        public void run() {
          int exitCode = 0;
          try {
            exitCode = process.waitFor();
          }
          catch (InterruptedException ignored) {
          }
          finally {
            try {
              myTerminationCallback.take().consume(exitCode);
            }
            catch (InterruptedException e) {
              // Ignore
            }
          }
        }
      });
    }

    public void setTerminationCallback(Consumer<Integer> r) {
      myTerminationCallback.offer(r);
    }
  }

  public Process getProcess() {
    return myProcess;
  }

  public void startNotify() {
    final ReadProcessRequest stdoutThread = new ReadProcessRequest(createProcessOutReader()) {
      protected void textAvailable(String s) {
        notifyTextAvailable(s, ProcessOutputTypes.STDOUT);
      }
    };

    final ReadProcessRequest stderrThread = new ReadProcessRequest(createProcessErrReader()) {
      protected void textAvailable(String s) {
        notifyTextAvailable(s, ProcessOutputTypes.STDERR);
      }
    };

    notifyTextAvailable(myCommandLine + '\n', ProcessOutputTypes.SYSTEM);

    addProcessListener(new ProcessAdapter() {
      public void startNotified(final ProcessEvent event) {
        try {
          final Semaphore outSemaphore = stdoutThread.schedule();
          final Semaphore errSemaphore = stderrThread.schedule();

          myWaitFor.setTerminationCallback(new Consumer<Integer>() {
            @Override
            public void consume(Integer exitCode) {
              try {
                // tell threads that no more attempts to read process' output should be made
                stderrThread.setProcessTerminated(true);
                stdoutThread.setProcessTerminated(true);

                outSemaphore.waitFor();
                errSemaphore.waitFor();
              }
              finally {
                onOSProcessTerminated(exitCode);
              }
            }
          });
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  protected void onOSProcessTerminated(final int exitCode) {
    notifyProcessTerminated(exitCode);
  }

  protected Reader createProcessOutReader() {
    return new BufferedReader(new InputStreamReader(myProcess.getInputStream(), getCharset()));
  }

  protected Reader createProcessErrReader() {
    return new BufferedReader(new InputStreamReader(myProcess.getErrorStream(), getCharset()));
  }

  protected void destroyProcessImpl() {
    try {
      closeStreams();
    }
    finally {
      myProcess.destroy();
    }
  }

  protected void detachProcessImpl() {
    final Runnable runnable = new Runnable() {
      public void run() {
        closeStreams();

        myWaitFor.detach();
        notifyProcessDetached();
      }
    };

    executeOnPooledThread(runnable);
  }

  private void closeStreams() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public boolean detachIsDefault() {
    return false;
  }

  public OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  // todo: to remove
  public String getCommandLine() {
    return myCommandLine;
  }


  public Charset getCharset() {
    return EncodingManager.getInstance().getDefaultCharset();
  }

  private abstract static class ReadProcessRequest {
    private static final int NOTIFY_TEXT_DELAY = 300;

    private final Reader myReader;

    private final StringBuffer myBuffer = new StringBuffer();
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    private boolean myIsClosed = false;
    private boolean myIsProcessTerminated = false;
    private final Semaphore mySemaphore = new Semaphore();

    public ReadProcessRequest(final Reader reader) {
      myReader = reader;
    }

    public synchronized boolean isProcessTerminated() {
      return myIsProcessTerminated;
    }

    public synchronized void setProcessTerminated(boolean isProcessTerminated) {
      myIsProcessTerminated = isProcessTerminated;
    }

    public Semaphore schedule() {
      myAlarm.addRequest(new Runnable() {
        public void run() {
          if(!isClosed()) {
            myAlarm.addRequest(this, NOTIFY_TEXT_DELAY);
            checkTextAvailable();
          }
        }
      }, NOTIFY_TEXT_DELAY);

      ourReaderThread.addRequest(this);

      mySemaphore.down();
      return mySemaphore;
    }

    public void readAvailable(char[] buffer) throws IOException {
      if (!isClosed()) {
        if (myReader.ready()) {
          int n = myReader.read(buffer);
          if (n > 0) {
            final boolean hasLineFeed;
            synchronized (myBuffer) {
              myBuffer.append(buffer, 0, n);
              hasLineFeed = myBuffer.indexOf("\n") >= 0;
            }

            if (hasLineFeed) {
              checkTextAvailable();
            }
          }
        }

        if (isProcessTerminated()) {
          close();
        }
      }
    }

    private void checkTextAvailable() {
      synchronized (myBuffer) {
        if (myBuffer.length() == 0) return;
        // warning! Since myBuffer is reused, do not use myBuffer.toString() to fetch the string
        // because the created string will get StringBuffer's internal char array as a buffer which is possibly too large.
        final String s = myBuffer.substring(0, myBuffer.length());
        myBuffer.setLength(0);
        textAvailable(s);
      }
    }

    private void close() {
      synchronized (this) {
        if (isClosed()) {
          return;
        }
        myIsClosed = true;
      }
      //try {
      //  if(Thread.currentThread() != this) {
      //    join(0);
      //  }
      //}
      //catch (InterruptedException e) {
      //}
      // must close after the thread finished its execution, cause otherwise
      // the thread will try to read from the closed (and nulled) stream
      try {
        myReader.close();
      }
      catch (IOException e1) {
        // supressed
      }
      checkTextAvailable();
      mySemaphore.up();
    }

    protected abstract void textAvailable(final String s);

    private synchronized boolean isClosed() {
      return myIsClosed;
    }
  }

  private static class ReaderThread extends Thread {
    private final BlockingQueue<ReadProcessRequest> queue = new LinkedBlockingQueue<ReadProcessRequest>();
    private final char[] myBuffer = new char[8192];
    

    @Override
    public void run() {
      while (true) {
        final ReadProcessRequest request = takeRequest();
        if (request == null) return;

        processRequest(request);
        if (!request.isClosed()) addRequest(request);

        Thread.yield();
      }
    }


    private void processRequest(ReadProcessRequest request) {
      try {
        request.readAvailable(myBuffer);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    @Nullable
    private ReadProcessRequest takeRequest() {
      try {
        return queue.take();
      }
      catch (InterruptedException e) {
        return null;
      }
    }

    public void addRequest(ReadProcessRequest request) {
      queue.offer(request);
    }

  }
}
