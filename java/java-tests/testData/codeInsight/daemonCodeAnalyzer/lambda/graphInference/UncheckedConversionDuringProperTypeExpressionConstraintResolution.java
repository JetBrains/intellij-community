class Test {
  private static AG<AE> foo(Class clz) {
    return (AG<AE>) foo1(clz);
  }

  private static <T extends E> G<T> foo1(Class<? extends E> clz) {
    return null;
  }

  interface E {}
  interface AE extends E {}

  interface G<T extends E> {}
  interface AG<T extends AE> extends G<T> {}

}

