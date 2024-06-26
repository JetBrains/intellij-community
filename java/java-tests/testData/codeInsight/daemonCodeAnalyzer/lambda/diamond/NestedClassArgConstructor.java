class R<T> {
  static class O {
    public O(<error descr="'R.this' cannot be referenced from a static context">T</error> t) {
    }
  }

  public static void main(String[] args) {
    test(new R<>.O<error descr="Expected 1 argument but found 0">()</error>);
  }

  private static void test(R.O o) { }
}