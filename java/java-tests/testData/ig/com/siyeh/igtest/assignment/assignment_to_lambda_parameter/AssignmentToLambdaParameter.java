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
    Runnable r = <error descr="Wrong number of parameters in lambda expression: expected 0 but found 1">a</error> -> a = "";
  }

}