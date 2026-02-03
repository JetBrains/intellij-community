// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(String s) {
      if (s == null || s.equals("zero")) {
          System.out.println(0);
      } else if (s.equals("one")) {
          System.out.println(1);
      }
  }
}