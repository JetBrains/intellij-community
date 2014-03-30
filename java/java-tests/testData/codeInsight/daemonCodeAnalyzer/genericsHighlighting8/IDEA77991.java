class Test {
    static {
        Class<Test> testClass = get(Test.class);
        foo(testClass);
        Test f = foo(testClass);
    }

    static <E> Class<E> get(Class<? super E> value) {
        return null;
    }

    static <E> E foo(Class<? super E> value) {
        return null;
    }
}

class Comp {
  public static <T> boolean equal(T arg1, T arg2) {
    return false;
  }

  void foo(String s, Object o) {
    if (equal(s, o)) {
    }
  }
}
