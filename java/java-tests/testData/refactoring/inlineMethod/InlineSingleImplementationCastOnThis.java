public class InlineSingleImplementation {
  interface MyIface {
    void mySimpleMethod();
  }

  static class MyIfaceImpl implements MyIface {
    @Override
    public void mySimpleMethod() {
      extracted();
    }

    private void extracted() {
      System.out.println("Impl");
    }
  }

  void test(MyIface iface) {
    iface.<caret>mySimpleMethod();
  }
}