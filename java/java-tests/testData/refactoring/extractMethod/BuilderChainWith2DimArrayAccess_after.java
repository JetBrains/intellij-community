class Foo {
    boolean bar(String[][] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = 0; i < a[i].length; j++) {
                if (newMethod(a[i][j], i)) return true;

            }
        return false;
    }

    private boolean newMethod(String s, int i) {
        if (s.length() > 3 && i % 3 == 0)
            return true;
        return false;
    }
}
