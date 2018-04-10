class Conditional {
    int[] bar(String[] s) {

        int[] n = newMethod(s);
        if (n != null) return n;
        return new int[0];
    }

    private int[] newMethod(String[] s) {
        if (s != null) {
            int[] n = new int[s.length];
            for (int i = 0; i < s.length; i++) {
                n[i] = s[i].length();
            }
            return n;
        }
        return null;
    }

    int[] baz(String[] z) {
        int[] n = newMethod(z);
        if (n != null) return n;
        return new int[0];
    }
}
