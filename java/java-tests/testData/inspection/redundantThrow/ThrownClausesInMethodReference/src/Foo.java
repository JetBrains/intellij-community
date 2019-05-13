import java.io.IOException;

class RedundantThrowsBug {

  static class Some{
    void doSomething() throws Exception {

      //no exception here

    }
  }

  interface ExceptionalComputable {
    void compute() throws Exception;
  }

  static void compute(ExceptionalComputable c) {

  }

  public static void main(Some some) {
    compute(some::doSomething);
  }
}