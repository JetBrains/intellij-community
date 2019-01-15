class Test {
      public static final Object FOO = new Test().foo;
  
      private final String foo;
  
      private Test() {
          foo = "some text";
      }
}
