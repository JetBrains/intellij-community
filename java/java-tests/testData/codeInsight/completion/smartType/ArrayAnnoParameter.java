public @interface A {
   String[] value();
}

class WontComplete {
   public static final String STRING = "aaaaa";

   @A(<caret>{"x"})
   int x;
}