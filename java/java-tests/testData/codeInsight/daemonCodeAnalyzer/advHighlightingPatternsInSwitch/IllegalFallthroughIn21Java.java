class IllegalFallthroughIn21Java {


  public static void test() {
    Object o = "Hello";
    switch (o) {
      case String s:
        System.out.println();
      case <error descr="Illegal fall-through to a pattern">Integer i</error>: 
        System.out.println();
      default:
        throw new IllegalStateException("Unexpected value: " + o);

    }
  }

  public static void test0() {
    Object o = "Hello";
    switch (o) {
      case null:
        System.out.println();
      case <error descr="Illegal fall-through to a pattern">Integer i</error>: 
        System.out.println();
      default:
        throw new IllegalStateException("Unexpected value: " + o);

    }
  }

  public static void test2() {
    Object o = "Hello";
    switch (o) {
      case String s:
        System.out.println();
        throw new UnsupportedOperationException();
      case Integer i:
        System.out.println();
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  public static void test3() {
    String o = "Hello";
    switch (o) {
      case String s when s.length() == 1:
        System.out.println();
      case "1":
        System.out.println();
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  public static void test4() {
    String o = "Hello";
    switch (o) {
      case String s when s.length() == 1:
        System.out.println();
      case "1":
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>); 
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  public static void test5() {
    String o = "Hello";
    switch (o) {
      case String s when s.length() == 1:
        System.out.println();
      case null:
        System.out.println();
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  public static void test6() {
    String o = "Hello";
    switch (o) {
      case String s when s.length() == 1:
        System.out.println();
      case null:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>); 
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + o);
    }
  }

  public static void test7() {
    Object obj = "Hello";
    switch (obj) {
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String s</error>: 
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>: 
        System.out.println();
    }
  }

  public static void test8() {
    Object obj = "Hello";
    switch (obj) {
      case null:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>: 
        System.out.println();
    }
  }

  public static void test9() {
    String obj = "Hello";
    switch (obj) {
      case "a":
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">String i</error> when i.length() == 1: 
        System.out.println();
    }
  }

  public static void test10() {
    Object obj = "Hello";
    switch (obj) {
      case null:
      case <error descr="Multiple switch labels are permitted for a switch labeled statement group only if none of them declare any pattern variables">Integer i</error>: 
        System.out.println();
    }
  }

  public static void test11() {
    record R1(){}
    record R2(){}
    Object obj = "Hello";
    switch (obj) {
      case String s:
        System.out.println(s);
      case R1():
        System.out.println();
        break;
      default:
    }

  }

  public static void test12() {
    record R1(){}
    record R2(){}
    Object obj = "Hello";
    switch (obj) {
      case String s:
        System.out.println(s);
      case R1():
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>); 
        break;
      default:
    }

  }

  public static void test13() {
    record R1(){}
    record R2(){}
    Object obj = "Hello";
    switch (obj) {
      case R1():
      case R2():
        System.out.println();
        break;
      default:
    }

  }

  public static void test14() {
    record R1(){}
    record R2(){}
    Object obj = null;
    switch (obj) {
      case null:
      case R1():
      case R2():
        System.out.println();
        break;
      default:
    }

  }
  void emptyCase(Object o) {
    switch (o) {
      case Integer a1:
      case Object a222:
        //                System.out.println("1");
    }
  }
}
