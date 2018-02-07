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
package com.intellij.util.io;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

final class ProgressMonitorInputStream extends InputStream {
  private final ProgressIndicator indicator;
  private final InputStream in;

  private final double available;
  private long count;

  public ProgressMonitorInputStream(@NotNull ProgressIndicator indicator, @NotNull InputStream in, int length) {
    this.indicator = indicator;
    this.in = in;
    available = length;
  }

  public int read() throws IOException {
    int c = in.read();
    updateProgress(c >= 0 ? 1 : 0);
    return c;
  }

  private void updateProgress(long increment) {
    indicator.checkCanceled();
    if (increment > 0) {
      count += increment;
      indicator.setFraction((double)count / available);
    }
  }

  public int read(byte[] b) throws IOException {
    int r = in.read(b);
    updateProgress(r);
    return r;
  }

  public int read(byte[] b, int off, int len) throws IOException {
    int r = in.read(b, off, len);
    updateProgress(r);
    return r;
  }

  public long skip(long n) throws IOException {
    long r = in.skip(n);
    updateProgress(r);
    return r;
  }

  public void close() throws IOException {
    in.close();
  }

  @Override
  public int available() throws IOException {
    return in.available();
  }
}
