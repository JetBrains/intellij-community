class A {
    void method<caret>(boolean a){}

   /**
     * {@link #method(boolean)}
     */
    void bar() {}
}
