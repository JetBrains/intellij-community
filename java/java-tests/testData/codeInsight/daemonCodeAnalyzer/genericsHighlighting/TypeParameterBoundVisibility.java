class A {
   private int value = 1;

   static class B<T extends A> {
      void print(T t) {
         System.out.println(t.<error descr="'value' has private access in 'A'">value</error>);
      }
   }
}