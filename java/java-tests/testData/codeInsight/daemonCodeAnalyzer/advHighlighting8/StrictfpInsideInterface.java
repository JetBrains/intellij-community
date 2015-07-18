interface A {
   <error descr="Illegal combination of modifiers: 'strictfp' and 'abstract'">strictfp</error> void m();
   strictfp default void m1(){}
   strictfp static void m2() {}
}