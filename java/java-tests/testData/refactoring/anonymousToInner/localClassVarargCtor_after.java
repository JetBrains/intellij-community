class B {
    void test(int x) {

        new InnerClass(new int[]{1, 2}, x).test();
    }

    private static class InnerClass {
        private final int x;

        InnerClass(int[] data, int x) {
            this.x = x;
        }
  
        void test() {
            System.out.println(x);
        }
    }
}