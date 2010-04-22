class A1<T> {
  A1(){}

  public static void main(String[] args) {

    Callable<Object> callable = new Callable<Object>() {
      public Object call() throws Exception {
        return new A1<Str<caret>ing>().toString();

      }
    };
    B b = null;
    b.bar(b.<String>foo("", ""));
  }


  <T> String foo(T t, String s) {
    return null;
  }

  String bar(String s) {
    return null;
  }
}