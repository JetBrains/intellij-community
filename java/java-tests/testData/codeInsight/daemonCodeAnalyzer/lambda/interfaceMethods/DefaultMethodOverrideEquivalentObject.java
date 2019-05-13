interface A {
    default String <error descr="Default method 'toString' overrides a member of 'java.lang.Object'">toString</error>() {
        return "";
    }
    default void finalize() throws Throwable { }
}