package com.siyeh.igfixes.tryCatch;

import java.io.FileInputStream;
import java.io.IOException;

class DeleteTryWithEmptyFinallyNoOuterBlock {
  void test() {
    if (Math.random() > 0.5) {
        System.out.println("x");
<caret>    }
  }
}