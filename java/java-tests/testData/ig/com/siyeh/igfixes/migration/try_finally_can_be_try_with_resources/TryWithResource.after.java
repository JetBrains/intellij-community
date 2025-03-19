/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
import java.io.*;

class X {
  public void resourceListExists() throws IOException {
      try (FileInputStream f1 = new FileInputStream("1"); FileInputStream f2 = new FileInputStream("2")) {

      }
  }
}