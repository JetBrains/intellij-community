class Test {
  {
    Comparable c = (<error descr="Incompatible parameter types in lambda expression">String o</error>)->{
      return 0;
    };
  }
}