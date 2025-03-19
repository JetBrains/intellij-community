class AssignmentToLambdaParameter {

  interface C {
    void consume(Object o);
  }

  static {
    C c = (o) -> {
      System.out.println(o);
      <warning descr="Assignment to lambda parameter 'o'">o</warning> = new Object();
      System.out.println(o);
    };
  }

  void x() {
    Runnable r = a -> <error descr="Incompatible types. Found: 'java.lang.String', required: '<lambda parameter>'">a = ""</error>;
  }

}