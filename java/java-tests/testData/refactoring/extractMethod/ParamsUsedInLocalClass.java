class A {
    void foo() {
      final int abc = 0;
      boolean b = false;
      <selection>
      if (b) {
        class T {
          void bar() {
            System.out.println(abc);
          }
        }
        return;
      } </selection>
      System.out.println("");
    }
}