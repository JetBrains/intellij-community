class OxfordBug {
    private int f(int m, int n) throws Exception {
        int i = 0;
        while (i < n) {
            i++;
            n = newMethod(m, n, i);
        }
        return n;
    }

    private int newMethod(int m, int n, int i) throws Exception {
        if (i == m) {
            n += m;
            throw new Exception("" + n);
        }
        return n;
    }
}