class B {
    void test(int x) {
        class X<caret> {
            X(int... data) {}
      
            void test() {
                System.out.println(x);
            }
        }
    
        new X(1, 2).test();
    }
}