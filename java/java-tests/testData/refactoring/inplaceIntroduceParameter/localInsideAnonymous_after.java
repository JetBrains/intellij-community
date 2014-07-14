class Abc {
  void foo() {
    final String name = "name";

    new Runnable(){
      @Override
      public void run() {
        foo(name);

      }

      private void foo(String name1) {
        System.out.println(name1);
      }
    };
  }
}

