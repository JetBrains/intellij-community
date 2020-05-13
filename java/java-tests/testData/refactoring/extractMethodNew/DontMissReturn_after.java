class Test {
    public void test(int x, int y) {
        while (x < 10) {
            x++;
            if (newMethod(x, y)) return;
        }
        System.out.println();
    }

    private boolean newMethod(int x, int y) {
        if (x == y) {
            System.out.println();
            return true;
        }
        return false;
    }
}