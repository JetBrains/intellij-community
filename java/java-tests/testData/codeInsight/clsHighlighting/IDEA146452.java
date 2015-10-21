package com.badinterface;

import java.util.List;
import java.util.concurrent.Future;

class Test {
  private void foo(BadInterface2 b, BadInterface3 b3) {
    final Future<? extends Number> process = b.process();
    final Future<? extends List<? extends Number>> process3 = b3.process();
  }
}
