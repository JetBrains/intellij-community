public class DeadCode {
  private static String m1(int n) {
    return "";
    <error descr="Unreachable statement">return</error> switch (1) {
      default -> "x";
    };
  }

  private static String m2(int n) {
    return "";
    <error descr="Unreachable statement">int x = switch (1) {
      default -> 0;
    };</error>
  }

  private static String m3(int n) {
    int x;
    return "";
    <error descr="Unreachable statement">x = switch (1) {
      default -> 0;
    };</error>
  }

  private static String m4(int n) {
    return "";
    <error descr="Unreachable statement">throw</error> switch (1) {
      default -> new RuntimeException();
    };
  }

  private static String m5(int n) {
    return "";
    <error descr="Unreachable statement">try</error> (AutoCloseable x = switch(1) {default -> () -> {};}) {} catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String m6(int n) {
    return "";
    <error descr="Unreachable statement">synchronized</error> (switch(1) {default -> "";}) {
      System.out.println("");
    }
  }

  private static String m7(int n) {
    return "";
    <error descr="Unreachable statement">if</error>(switch (1) {default -> true;}) {}
  }

  private static String m8(int n) {
    return switch (n) {
      default:
        yield "x";
        <error descr="Unreachable statement">yield</error> switch(1) { default -> ""; };
    };
  }

  private static String m9() {
    return "";
    <error descr="Unreachable statement">assert</error> switch(1) {default -> true;};
  }
}