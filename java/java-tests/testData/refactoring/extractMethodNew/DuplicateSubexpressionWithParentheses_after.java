class C {
    int x;

    int b(boolean[] b, C[] c, int n) {
        int i = n;
        while (i >= 0 && newMethod(b[i], c[n].x == c[i].x)) {
            i--;
        }
        return i;
    }

    private boolean newMethod(boolean b2, boolean b1) {
        return b2 || b1;
    }

    int a(boolean[] b, C[] c, int n) {
        int i = n;
        while (i < c.length && (newMethod(b[i], c[n].x == c[i].x))) {
            i++;
        }
        return i;
    }
}