/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public abstract class ChildVMStarter {

  protected abstract void configureFrameworkAndRun(String[] args, PrintStream out, PrintStream err) throws Exception;
  
  //setup output wrappers
  protected void startVM(String[] args) throws Exception {
    final String testOutputPath = args[0];
    final File file = new File(testOutputPath);
    if (!file.exists()) {
      if (!file.createNewFile()) return;
    }
    final FileOutputStream stream = new FileOutputStream(testOutputPath);
    //noinspection UseOfSystemOutOrSystemErr
    PrintStream oldOut = System.out;
    //noinspection UseOfSystemOutOrSystemErr
    PrintStream oldErr = System.err;
    try {
      final PrintStream out = new PrintStream(new ForkedVMWrapper(stream, false));
      final PrintStream err = new PrintStream(new ForkedVMWrapper(stream, true));
      System.setOut(out);
      System.setErr(err);
      configureFrameworkAndRun(args, out, err);
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      stream.close();
    }
  }
}
