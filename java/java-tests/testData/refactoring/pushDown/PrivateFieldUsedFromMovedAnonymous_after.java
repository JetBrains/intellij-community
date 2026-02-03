
class A {
  private String prefix = "> ";

}

class B extends A {
    void foo() {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          System.out.println(prefix);
        }
      };
    }
}