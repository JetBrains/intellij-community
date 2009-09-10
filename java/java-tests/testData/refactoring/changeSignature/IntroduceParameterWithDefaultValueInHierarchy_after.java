class Foo {
   void foo(int i){}

   class A extends Foo {
     void foo(int i){
       super.foo(i);
     }
   }
}