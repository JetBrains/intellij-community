class Conditional {
    int bar(String s) {<selection>
        if (s != null) {
            int n = s.length;
            return n;
        }</selection>
        return 0;
    }

    int baz(String z) {
        int x = -1;
        if (z != null) {
            int n = z.length;
            x = n;
        }
        return 0;
    }
}
