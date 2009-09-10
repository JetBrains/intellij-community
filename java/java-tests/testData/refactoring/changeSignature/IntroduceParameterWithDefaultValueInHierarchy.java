class Foo {
   void f<caret>oo(){}

   class A extends Foo {
     void foo(){
       super.foo();
     }
   }
}