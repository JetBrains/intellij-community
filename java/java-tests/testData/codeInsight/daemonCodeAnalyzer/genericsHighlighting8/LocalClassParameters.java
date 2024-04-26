class C<A> {
  static <T> Object foo(Object x) {
    class Local {
    }
    return (x instanceof Local) ? x : new Local();
  }

  static <T> Object foo2(Object x) {
    class Local<T> {
    }
    return (x instanceof Local) ? x : new Local();
  }

  Object foo3(Object x) {
    class Local {
    }
    return (x instanceof <error descr="Illegal generic type for instanceof">Local</error>) ? x : new Local();
  }

  public static void main(String[] args) {
    System.out.println(C.<Integer>foo(""));
  }
}