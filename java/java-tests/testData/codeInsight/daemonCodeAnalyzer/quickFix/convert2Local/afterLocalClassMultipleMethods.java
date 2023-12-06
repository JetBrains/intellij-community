// "Convert to local" "true-preview"
class Outer {

  void test() {
    class Local {

        void foo() {
            String s = "1";
        System.out.println(s);
      }

      void bar() {
          String s = "2";
        System.out.println(s);
      }

    }
  }
}