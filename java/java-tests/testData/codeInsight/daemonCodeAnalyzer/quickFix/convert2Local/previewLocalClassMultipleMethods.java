// "Convert to local" "true-preview"
class Outer {

  void test() {
    class Local {

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