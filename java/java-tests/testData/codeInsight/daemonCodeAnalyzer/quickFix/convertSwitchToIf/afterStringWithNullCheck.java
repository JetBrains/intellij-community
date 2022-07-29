// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(String s) {
      if (s == null || "zero".equals(s)) {
          System.out.println(0);
      } else if ("one".equals(s)) {
          System.out.println(1);
      }
  }
}