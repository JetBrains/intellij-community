public class PrivateRecordInsideAnonymous {
  void foo() {
    new Object() {
      private record Foo() { }
    };
    class Local {
      private class Priv {}
      protected class Prot {}
    }
  }
}