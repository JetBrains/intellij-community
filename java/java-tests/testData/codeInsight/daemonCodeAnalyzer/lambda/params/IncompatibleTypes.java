class XXX {
  Runnable bar() {
    return <error descr="Incompatible parameter types in lambda expression">(o)->{
      System.out.println();
    }</error>;
  }
}