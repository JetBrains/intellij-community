public class Bug17 {

  private void f(byte[] d) {
  }

  private void g() {
    f(new <caret>);
  }

}