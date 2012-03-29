class A {
   private int value = 1;

   static class B<T extends A> {
      void print(T t) {
         System.out.println(t.<error descr="'value' has private access in 'A'">value</error>);
      }
   }
}

abstract class Foo<T extends Foo<T>> {
    private int field;

    public int bar(T t){
        return t.<error descr="'field' has private access in 'Foo'">field</error>;
    }
}
