public class UnnamedPatterns {
  record R(int a, int b) {}
  
  void test(Object obj) {
    if (obj instanceof <error descr="As of Java 9, '_' is a keyword, and may not be used as an identifier">_</error>) {}
    
    if (obj instanceof R(_, _)) {}
    if (obj instanceof R(int a, _)) {
      System.out.println(a);
    }
    if (obj instanceof R(_, int b)) {
      System.out.println(b);
    }
    if (obj instanceof R(_, _, <error descr="Incorrect number of nested patterns: expected 2 but found 3">_)</error>) {
    }
  }

  void testSwitch(Object obj) {
    switch (obj) {
      case R(_, var b) -> {
      }
      case <error descr="Label is dominated by a preceding case label 'R(_, var b)'">R(var c, var b)</error> -> {
      }
      case <error descr="Label is dominated by a preceding case label 'R(_, var b)'">R(int a, _)</error> -> {
      }
      default -> {
      }
    }
  }
}