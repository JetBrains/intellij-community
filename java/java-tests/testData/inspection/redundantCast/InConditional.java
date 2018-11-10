class A {
   {
     String s = true ? "" : (<warning descr="Casting 'null' to 'String' is redundant">String</warning>) null; //cast is needed
   }
}