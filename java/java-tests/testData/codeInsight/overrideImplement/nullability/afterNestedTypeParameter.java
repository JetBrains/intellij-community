package mypackage;

import org.jetbrains.annotations.NotNull;

class Outer {
  static class Middle {
    static class Nested {
    }
  }
}

interface Parent {
  void test(@NotNull Outer.Middle.Nested nested);
}

class Child implements Parent {
    @Override
    public void test(@NotNull Outer.Middle.Nested nested) {
        
    }
}
