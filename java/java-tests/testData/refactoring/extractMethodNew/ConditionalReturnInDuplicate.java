class Conditional {
    int[] bar(String[] s) {
        <selection>
        if (s != null) {
            int[] n = new int[s.length];
            for (int i = 0; i < s.length; i++) {
                n[i] = s[i].length();
            }
            return n;
        }</selection>
        return new int[0];
    }

    int[] baz(String[] z) {
        if (z != null) {
            int[] n = new int[z.length];
            for (int i = 0; i < z.length; i++) {
                n[i] = z[i].length();
            }
            return n;
        }
        return new int[0];
    }
}
