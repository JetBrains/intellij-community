/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.rt.execution.testFrameworks;

import java.io.*;

/**
* User: anna
* Date: 4/6/11
*/
class ForkedVMWrapper extends DataOutputStream {

  public static final char DELIMITER = '\u0002';
  public static final byte[] ERROR = new byte[] {'\u0003'};
  public static final byte[] OUTPUT = new byte[] {'\u0004'};
  private FileOutputStream myOutputStream;
  private boolean myError;

  public ForkedVMWrapper(FileOutputStream outputStream, boolean error) throws FileNotFoundException {
    super(outputStream);
    myOutputStream = outputStream;
    myError = error;
  }

  public synchronized void write(int b) throws IOException {
    printPrefix();
    myOutputStream.write(b);
  }

  private void printPrefix() throws IOException {
    myOutputStream.write(("/" + DELIMITER).getBytes());
    if (myError) {
      myOutputStream.write(ERROR);
    }
    else {
      myOutputStream.write(OUTPUT);
    }
  }

  public void write(byte[] b) throws IOException {
    printPrefix();
    myOutputStream.write(b);
  }

  public synchronized void write(byte[] b, int off, int len) throws IOException {
    printPrefix();
    myOutputStream.write(b, off, len);
  }

  public void close() throws IOException {
    myOutputStream.close();
  }

  public void flush() throws IOException {
    myOutputStream.flush();
  }

  public static void readWrapped(String path, PrintStream out, PrintStream err) throws IOException {
    FileInputStream stream = new FileInputStream(path);
    try {
      boolean error = false;
      boolean afterSymbol = false;
      boolean afterDelimiter = false;
      while (stream.available() > 0) {
        char read = (char)stream.read();
        if (!afterSymbol && read == '/') {
          afterSymbol = true;
          continue;
        }
        if (afterSymbol) {
          if (afterDelimiter) {
            error = read == ERROR[0];
            afterSymbol = false;
            afterDelimiter = false;
            continue;
          }
          if (read != DELIMITER) {
            if (error) {
              err.write("/".getBytes());
              err.write(read);
            }
            else {
              out.write("/".getBytes());
              out.write(read);
            }
            afterSymbol = false;
            afterDelimiter = false;
            continue;
          }
          else {
            afterDelimiter = true;
            continue;
          }
        }
        if (error) {
          err.write(read);
        }
        else {
          out.write(read);
        }
      }
    }
    finally {
      if (stream != null) stream.close();
    }
  }
}
