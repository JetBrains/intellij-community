// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  void test(boolean b) {
    StringBuilder sb = new StringBuilder();
    String s;
    if (b) {
        sb.append("foo");
        sb.append("bar");
        s = <caret>sb.toString();
    }
  }
}