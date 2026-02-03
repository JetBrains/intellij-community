package com.siyeh.ipp.exceptions.detail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

class TryWithResources {

  void foo(File file1, File file2) {
    <caret>try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {
      throw new IllegalArgumentException();
    } catch (Exception e) {}
  }
}