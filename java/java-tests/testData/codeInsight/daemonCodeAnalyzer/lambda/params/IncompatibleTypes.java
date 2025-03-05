class XXX {
  Runnable bar() {
    return <error descr="Wrong number of parameters in lambda expression: expected 0 but found 1">(o)</error>->{
      System.out.println();
    };
  }
}