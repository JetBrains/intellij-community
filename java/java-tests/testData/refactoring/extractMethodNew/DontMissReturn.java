class Test {
    public void test(int x, int y) {
        while (x < 10) {
            x++;
            <selection>if (x == y) {
                System.out.println();
                return;
            }</selection>
        }
        System.out.println();
    }
}