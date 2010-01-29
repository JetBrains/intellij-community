public class Simple {}

class Usage {
  void foo() {
    synchronized (new Si<caret>mple()) {
      //dosmth
    }
  }
}