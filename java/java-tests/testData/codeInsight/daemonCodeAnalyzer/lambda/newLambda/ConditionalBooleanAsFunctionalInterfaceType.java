class Test {
  {
    boolean condition = <error descr="boolean is not a functional interface">() -> {}</error> ? true : false;
  }
}