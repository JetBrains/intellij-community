class Test {
  void test(Number n) {
    class MyNumber extends Number {
      @Override
      public int intValue() {
        return 0;
      }

      @Override
      public long longValue() {
        return 0;
      }

      @Override
      public float floatValue() {
        return 0;
      }

      @Override
      public double doubleValue() {
        return 0;
      }
    }
    int result;

    switch (n) {
      case <error descr="Type pattern expected">MyNumber</error>: break;
      case <error descr="Type pattern expected">Integer</error><error descr="':' or '->' expected"> </error>break;
      default: break;
    }
    result = switch (n) {
      case <error descr="Type pattern expected">MyNumber</error>: yield 1;
      case Float ignored: yield 2;
      default: yield 3;
    };
    result = switch (n) {
      case <error descr="Type pattern expected">MyNumber</error> -> 1;
      case Float ignored -> 2;
      default -> 3;
    };
  }
}