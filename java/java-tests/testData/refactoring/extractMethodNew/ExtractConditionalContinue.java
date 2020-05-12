class Test {
    public void test(int x, int y) {
        while (x < y) {
            <selection>if (x == y) {
                System.out.println();
                continue;
            }</selection>
            x++;
        }
    }
}