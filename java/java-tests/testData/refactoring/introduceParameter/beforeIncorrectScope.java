public class InnerOuter {
  String anObject = "";

  interface Inner {
    void exec();
  }

  Inner instance = new Inner() {
    public void exec() {
      <selection>anObject</selection>.charAt(1);
    }
  };

  void foo() {
   instance.exec();
  }
}
