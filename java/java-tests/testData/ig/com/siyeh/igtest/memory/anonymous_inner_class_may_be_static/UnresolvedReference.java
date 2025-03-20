class UnresolvedReference {
  void foo() {
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        int a = 4;
        System.out.println(a + <error descr="Cannot resolve symbol 'doesNotExist'">doesNotExist</error>);
      }
    });
  }
}
