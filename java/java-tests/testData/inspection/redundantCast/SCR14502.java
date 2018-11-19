class Test {
   public void foo(Test t) {
     foo(((<warning descr="Casting 'new Test()' to 'Test' is redundant">Test</warning>) new Test()));
   }
}