class Test {
  void test() {
    int i = <error descr="'switch' expression does not have any result expressions">switch</error>(0) {
      default -> throw new NullPointerException();
    };
  }
  
  void test2() {
    int i = <error descr="'switch' expression does not have any result expressions">switch</error>(0) {
      case 0 -> {while(true);}
      case 1 -> {
        throw new RuntimeException();
      }
      default -> throw new NullPointerException();
    };
    
  }
  
  void positive() {
    int i = switch(0) {
      case 4 -> 2;
      default -> throw new NullPointerException();
    };
  }

  Runnable lambdaContext(int x) {
    return switch (x) {
      default -> x > 0 ? () -> {} : () -> {};
    };
  }
  
  Object invalidLambdaContext(int x) {
    return (Runnable) switch (x) {
      default -> x > 0 ? <error descr="Unexpected lambda expression">() -> {}</error> : <error descr="Unexpected lambda expression">() -> {}</error>;
    };
  }
}