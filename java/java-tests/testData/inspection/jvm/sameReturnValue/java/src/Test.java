class Test {

  int xxx() {
    return 0;
  }

  void test() {
    Comparator<String> cmp = new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        return 0;
      }
    };
  }

  public static void main(String[] args) {
    System.out.println(new Test().xxx());
    new Test().test();
  }
}