class A {
    void foo() {
      final int abc = 0;
      boolean b = false;

        if (newMethod(abc, b)) return;
        System.out.println("");
    }

    private boolean newMethod(final int abc, boolean b) {
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