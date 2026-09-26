class Test {
  public void test0() {
    switch (null) {
      case String a -> System.out.println("");
    }
  }
  public void test() {
    switch (<error descr="'switch' statement does not cover all possible input values">null</error>) { //error
      case null -> System.out.println("");
    }
  }
  public void test2() {
    switch (null) {
      default -> System.out.println("");
    }
  }
  public void test3() {
    switch (<error descr="'switch' statement does not have any case clauses">null</error>) { //error
    }
  }

  enum E{A}
  public void test4() {
    switch (null) {
      case E.<error descr="Incompatible types. Found: 'Test.E', required: 'null'">A</error> -> System.out.println("2");  //error
    }
  }
  public void test5() {
    switch (null) {
      case E a -> System.out.println("");
    }
  }

  public void test6() {
    switch (<error descr="'switch' statement does not cover all possible input values">null</error>) { //error
      case E a when a.hashCode() ==1 -> System.out.println("");
    }
  }

  public void test7() {
    switch (null) {
      case <error descr="Duplicate unconditional pattern">String a</error> -> System.out.println("2"); //error
      case <error descr="Duplicate unconditional pattern">CharSequence a</error> -> System.out.println("2"); //error
    }
  }
}