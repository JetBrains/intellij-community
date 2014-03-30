class Abc {
  void foo() {
    final String name = "name";

    new Runnable(){
      @Override
      public void run() {
        foo();

      }

      private void foo() {
        System.out.println(na<caret>me);
      }
    };
  }
}

