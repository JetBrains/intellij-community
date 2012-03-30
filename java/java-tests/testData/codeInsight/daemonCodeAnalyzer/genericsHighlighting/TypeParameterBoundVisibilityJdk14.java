class A {
   private int value = 1;

   static class B<T extends A> {
      void print(T t) {
         System.out.println(t.value);
      }
   }
}