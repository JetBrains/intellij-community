class R<T> {
  static class O {
    public O(<error descr="'R.this' cannot be referenced from a static context">T</error> t) {
    }
  }

  public static void main(String[] args) {
    test(new R<>.O<error descr="'O(java.lang.Object)' in 'R.O' cannot be applied to '()'">()</error>);
  }

  private static void test(R.O o) { }
}