class Test {
   public void foo() {
      boolean b = <warning descr="Condition 'this instanceof Object' is always 'true'">this instanceof Object</warning>;
   }
}