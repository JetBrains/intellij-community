class Abc {
  void foo() {
    
    new Runnable(){
      @Override
      public void run() {
        final String na<caret>me = "name";
        System.out.println(name);
      }
    };
  }
}

