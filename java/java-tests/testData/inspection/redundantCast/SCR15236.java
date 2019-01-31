class Test {
   public void test(Object s) {
      Object o = ((<warning descr="Casting 's' to 'String' is redundant">String</warning>) s);
   }
}