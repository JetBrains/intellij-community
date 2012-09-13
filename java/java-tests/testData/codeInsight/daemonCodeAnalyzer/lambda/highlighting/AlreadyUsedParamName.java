class Test {
  {
    Object o = null;
    Comparable<String> c = <error descr="Variable 'o' is already defined in the scope">o</error> -> 42;
  }
}