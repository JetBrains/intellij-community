class Abc {
  void foo(final String name) {
    
    new Runnable(){
      @Override
      public void run() {
          System.out.println(name);
      }
    };
  }
}

