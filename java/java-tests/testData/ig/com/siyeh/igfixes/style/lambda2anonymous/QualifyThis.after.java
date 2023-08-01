class Test {
  public void m() {
    Runnable r = new Runnable() {
        @Override
        public void run() {System.out.println(Test.this);}
    };
    r.run();
  }
}