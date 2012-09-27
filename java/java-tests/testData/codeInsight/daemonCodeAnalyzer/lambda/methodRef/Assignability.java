class Test {
  {
      <error descr="Incompatible types. Found: '<method reference>', required: 'java.lang.Runnable'">Runnable b = Test :: length;</error>
      Comparable<String> c = Test :: length;
      <error descr="Incompatible types. Found: '<method reference>', required: 'java.lang.Comparable<java.lang.Integer>'">Comparable<Integer> c1 =  Test :: length;</error>
  }

  public static Integer length(String s) {
    return s.length();
  }

  interface Bar {
    Integer _(String s);
  }
}

class Test1 {
    {
        <error descr="Incompatible types. Found: '<method reference>', required: 'java.lang.Runnable'">Runnable b = Test1 :: length;</error>
        Comparable<String> c = Test1 :: length;
        Comparable<Integer> c1 =  Test1 :: length;
    }
  
    public static Integer length(String s) {
      return s.length();
    }

    public static Integer length(Integer s) {
      return s;
    }
  
    interface Bar {
      Integer _(String s);
    }
}

class Test2 {

    void foo(Integer i) {}

    <error descr="Incompatible types. Found: '<method reference>', required: 'java.lang.Object'">Object o = Test2::foo;</error>
}