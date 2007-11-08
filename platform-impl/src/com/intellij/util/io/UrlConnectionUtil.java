/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author nik
 */
public class UrlConnectionUtil {
  private UrlConnectionUtil() {
  }

  public static @Nullable InputStream getConnectionInputStream(URLConnection connection, ProgressIndicator pi) {
    try {
      return getConnectionInputStreamWithException(connection, pi);
    }
    catch (ProcessCanceledException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }


  public static InputStream getConnectionInputStreamWithException(URLConnection connection, ProgressIndicator pi) throws IOException {
    InputStreamGetter getter = new InputStreamGetter(connection);
    final Future<?> getterFuture = ApplicationManager.getApplication().executeOnPooledThread(getter);

    while (true) {
      pi.checkCanceled();
      try {
        try {
          getterFuture.get(50, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {}
        pi.setIndeterminate(true);
        pi.setText(pi.getText());
        if (getterFuture.isDone()) break;
      }
      catch (Exception e) {
        throw new ProcessCanceledException(e);
      }
    }
    if (getter.getException() != null) {
      throw getter.getException();
    }

    return getter.getInputStream();
  }

  public static class InputStreamGetter implements Runnable {
    private InputStream myInputStream;
    private URLConnection myUrlConnection;
    private IOException myException;

    public InputStreamGetter(URLConnection urlConnection) {
      myUrlConnection = urlConnection;
    }

    public IOException getException() {
      return myException;
    }

    public InputStream getInputStream() {
      return myInputStream;
    }

    public void run() {
      try {
        myInputStream = myUrlConnection.getInputStream();
      }
      catch (IOException e) {
        myException = e;
        myInputStream = null;
      }
      catch (Exception e) {
        myException = new IOException();
        myException.initCause(e);
        myInputStream = null;
      }
    }
  }
}
