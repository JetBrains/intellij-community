class XXX {
  Runnable bar() {
    return <error descr="Incompatible parameter types in lambda expression: wrong number of parameters: expected 0 but found 1">(o)</error>->{
      System.out.println();
    };
  }
}