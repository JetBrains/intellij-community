class Box {
  private void test(String str1, String str2) {
    Data data = <selection>new Data() {
      @Override
      public String getA() {
        return str1;
      }
      @Override
      public String getB() {
        return str2;
      }
    }</selection>;
    System.out.println(data);
  }

  static interface Data {
    String getA();
    String getB();
  }
}