public class Outer {
  Inner inner = new Inner();
  Other other = new Other();

  String param = "value";

  public class Inner {
    void doInner() {
      doOuter();
    }
  }

  void doOut<caret>er() {
    other.doOther(param);
  }
}

class Other {
  void doOther(String param) {
  }
}