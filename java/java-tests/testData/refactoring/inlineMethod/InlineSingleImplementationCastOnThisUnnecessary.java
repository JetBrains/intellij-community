public class InlineSingleImplementation {
  interface MyIface {
    void mySimpleMethod();
  }

  static class MyIfaceImpl implements MyIface {
    @Override
    public void mySimpleMethod() {
      System.out.println(toString());
      System.out.println(this.toString());
    }
  }

  void test(MyIface iface) {
    iface.<caret>mySimpleMethod();
  }
}