// "Cast to 'A'" "true-preview"
class A {
   void foo(Object foo) {
       if(foo insta<caret>nceof A) {
           System.out.println("");
       }
   }
}