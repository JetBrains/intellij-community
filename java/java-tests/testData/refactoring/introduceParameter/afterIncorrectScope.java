public class InnerOuter {
  String anObject = "";

  interface Inner {
    void exec(final String anObject);
  }

  Inner instance = new Inner() {
    public void exec(final String anObject) {
      anObject.charAt(1);
    }
  };

  void foo() {
   instance.exec(anObject);
  }
}
