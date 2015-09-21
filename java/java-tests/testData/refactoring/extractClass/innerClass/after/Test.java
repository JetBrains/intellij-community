class Test {
    final Extracted extracted = new Extracted(this);

    void bar(){
    System.out.println(extracted.getMyT());
  }

  String foo() {
    return "";
  }

  void bazz() {
    bar();
  }

    public static class Extracted {
        private final Test test;
        String myT;

        public String getMyT() {
            return myT;
        }

        public Extracted(Test test) {
            this.test = test;
            this.myT = test.foo();
        }
    }
}