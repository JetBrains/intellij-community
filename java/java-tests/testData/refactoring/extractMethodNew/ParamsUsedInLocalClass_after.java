class A {
    void foo() {
      final int abc = 0;
      boolean b = false;

        if (newMethod(b, abc)) return;
        System.out.println("");
    }

    private boolean newMethod(boolean b, final int abc) {
        if (b) {
          class T {
            void bar() {
              System.out.println(abc);
            }
          }
            return true;
        }
        return false;
    }
}