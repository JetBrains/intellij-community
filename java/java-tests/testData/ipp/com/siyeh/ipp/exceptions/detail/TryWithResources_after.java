package com.siyeh.ipp.exceptions.detail;

import java.io.*;

class TryWithResources {

  void foo(File file1, File file2) {
      try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {
          throw new IllegalArgumentException();
      } catch (FileNotFoundException e) {
      } catch (IOException e) {
      } catch (IllegalArgumentException e) {
      }
  }
}