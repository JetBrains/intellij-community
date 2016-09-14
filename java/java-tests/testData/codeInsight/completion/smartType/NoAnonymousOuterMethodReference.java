class Outer {
  void foo2(int a){
    new Object() {
      void run(int a) {
        class Inner {
          void bar() {
            Runnable r = fo<caret>
          }
        }
      }

      void foo() {}
    };
  }
}