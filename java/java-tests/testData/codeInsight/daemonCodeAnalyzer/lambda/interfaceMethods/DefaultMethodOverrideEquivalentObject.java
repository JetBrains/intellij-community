interface A {
    default String <error descr="Default method 'toString' overrides a member of 'java.lang.Object'">toString</error>() {
        return "";
    }
    default void finalize() throws Throwable { }
    boolean equals(Object o);
}

interface B extends A {

  default boolean <error descr="Default method 'equals' overrides a member of 'java.lang.Object'">equals</error>(Object o) {
    return true;
  }
}