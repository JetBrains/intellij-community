class Test {
  {
    Comparable c = <error descr="Incompatible parameter types in lambda expression: expected Object but found String">(String o)</error>->{
      return 0;
    };
  }
}