class Foo {
    boolean bar(String[][] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = 0; i < a[i].length; j++) {
                if (newMethod(a, i, j)) return true;

            }
        return false;
    }

    private boolean newMethod(String[][] a, int i, int j) {
        if (a[i][j].length() > 3 && i % 3 == 0) {
            System.out.println();
            return true;
        }
        return false;
    }
}
