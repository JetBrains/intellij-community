// "Cast to 'A'" "true-preview"
class A {
   void foo(Object foo) {
       if(foo instanceof A) {
           ((A) foo)
           System.out.println("");
       }
   }
}