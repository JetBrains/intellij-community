package com.siyeh.igfixes.tryCatch;

import java.io.FileInputStream;
import java.io.IOException;

class DeleteTryWithResources {

  void foo() throws IOException {
    try (FileInputStream in = new FileInputStream("")) {
      in.read();
    }
  }
}