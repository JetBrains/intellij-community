
class A<T>  {
  class B {
    private String foo;

    public void run() {
      new B() {
        @Override
        public void run() {
          moo(A.this);
        }
      };
    }

    void m<caret>oo(A a) {
      System.out.println(foo);
    }
  }

}