class C {
    int x;

    int b(boolean[] b, C[] c, int n) {
        int i = n;
        while (i >= 0 && <selection>(b[i] || c[n].x == c[i].x)</selection>) {
            i--;
        }
        return i;
    }

    int a(boolean[] b, C[] c, int n) {
        int i = n;
        while (i < c.length && (b[i] || c[n].x == c[i].x)) {
            i++;
        }
        return i;
    }
}