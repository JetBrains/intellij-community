class Test {
    public void test(int x, int y) {
        while (x < 10) {
            x++;
            if (newMethod(x, y)) break;
        }
        System.out.println();
    }

    private boolean newMethod(int x, int y) {
        if (x == y) return true;
        return false;
    }
}