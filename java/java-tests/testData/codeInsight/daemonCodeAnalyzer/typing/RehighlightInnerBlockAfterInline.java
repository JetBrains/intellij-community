class Ds {
    public int meth(int j) {
        int <caret>e = j;
        int d = e;
        if (j==0) {
            int e = j;
        }
        return 0;
    }
}