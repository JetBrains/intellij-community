class Test {

    void f(String strings) {
      extract("a", "b", "c", "d");
    }

    private static void extract(final String from, final String to, final String... extensions)  {

      <selection>foo(extensions)</selection>;
    }

    public String foo(String[] extensions) {
      return null
    }

}
