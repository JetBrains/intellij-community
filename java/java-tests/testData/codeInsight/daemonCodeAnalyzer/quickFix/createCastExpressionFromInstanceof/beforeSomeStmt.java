// "Cast to 'A'" "true"
class A {
   void foo(Object foo) {
       if(foo insta<caret>nceof A) {
           System.out.println("");
       }
   }
}