class ForIf {
    String foo(int[] a, boolean b) {
        for (int x : a) <selection>if (b) {
            String s = bar(x);
            if (s != null) return s;
        }</selection>

        return null;
    }

    String bar(int x) { return "";}
}
