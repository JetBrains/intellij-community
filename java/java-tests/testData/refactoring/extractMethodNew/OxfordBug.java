class OxfordBug {
    private int f(int m, int n) throws Exception {
        int i = 0;
        while (i < n) {
            i++;
            <selection>if (i == m) {
                n += m;
                throw new Exception("" + n);
            }</selection>
        }
        return n;
    }
}