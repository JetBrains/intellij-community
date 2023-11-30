public class VarCaptureForLoop {
  private void foo() {}
  public void bar(Iterable<? extends VarCaptureForLoop> data) {
    for (var it : data) it.foo();
  }
}