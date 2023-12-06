package com.siyeh.ipp.exceptions.splitMultiCatch;

import java.io.*;

public class Simple {
  void foo() {
    try {
      Reader reader = new FileReader("");
    } catch (//c1
            IndexOutOfBoundsException e) {
    } catch (//c1
            FileNotFoundException e) {
    }
  }
}