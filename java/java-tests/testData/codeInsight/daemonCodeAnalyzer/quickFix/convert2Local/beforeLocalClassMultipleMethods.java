// "Convert to local variable" "true-preview"
class Outer {

  void test() {
    class Local {
      private String <caret>s;

      void foo() {
        s = "1";
        System.out.println(s);
      }

      void bar() {
        s = "2";
        System.out.println(s);
      }

    }
  }
}