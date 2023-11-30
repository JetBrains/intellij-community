// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
public class Test {

  void m() {
      try {
          createModuleNode(null);
      } catch (A e) {
          e.printStackTrace();
      }
  }

  protected Object createModuleNode(Object module) throws B1, B2, B3, B4 {
    return null;
  }

  class A extends Exception {}
  class B1 extends A {}
  class B2 extends A {}
  class B3 extends A {}
  class B4 extends A {}
}
