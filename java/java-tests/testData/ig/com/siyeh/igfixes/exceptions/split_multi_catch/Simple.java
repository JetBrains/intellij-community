package com.siyeh.ipp.exceptions.splitMultiCatch;

import java.io.*;

public class Simple {
  void foo() {
    try {
      Reader reader = new FileReader("");
    } <caret>catch (IndexOutOfBoundsException //c1
                    | FileNotFoundException e) {
    }
  }
}