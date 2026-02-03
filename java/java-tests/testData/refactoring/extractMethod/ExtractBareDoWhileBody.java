class DoIfWhile {
    String foo(int a, boolean b) {
        int x = 0;
        do <selection>/*comment*/ if (b) {
            String s = bar(x);
            if (s != null) return s;
        }</selection>
        while (++x < a);

        return null;
    }

    String bar(int x) { return "";}
}