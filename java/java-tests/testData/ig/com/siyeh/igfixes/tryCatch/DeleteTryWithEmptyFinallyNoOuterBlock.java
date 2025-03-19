package com.siyeh.igfixes.tryCatch;

import java.io.FileInputStream;
import java.io.IOException;

class DeleteTryWithEmptyFinallyNoOuterBlock {
  void test() {
    if (Math.random() > 0.5)
      try {
        System.out.println("x");
      }
      fi<caret>nally {
      }
  }
}