public class ModifiersInsideAnonymousLocal {
  void foo() {
    new Object() {
      private record Foo() { }
      public record Foo1() {}
    };
    class Local {
      private class Priv {}
      protected class Prot {}
      public class Pub {}
    }
  }
}