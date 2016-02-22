class Test {
  {
      Runnable b = Test :: <error descr="Cannot resolve method 'length'">length</error>;
      Comparable<String> c = Test :: length;
      Comparable<Integer> c1 =  Test :: <error descr="Invalid method reference: Integer cannot be converted to String">length</error>;
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
        Runnable b = Test1 :: <error descr="Cannot resolve method 'length'">length</error>;
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

    Object o = <error descr="Object is not a functional interface">Test2::foo</error>;
}