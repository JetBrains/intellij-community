class Sample {
  Runnable runnable = new Runnable() {
    int field;

    @Override
    public void run() {
      class X {
        void test(int a) {
          String fi<caret>el = "";
          System.out.println(field);
        }
      }
    }
  };
}