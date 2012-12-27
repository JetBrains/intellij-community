// "Cast to 'A'" "true"
class A {
   void foo(Object foo) {
       if(foo instanceof A) {
           ((A) foo)
           System.out.println("");
       }
   }
}