class Foo {
    boolean bar(String[][] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = 0; i < a[i].length; j++) {<selection>
                if (a[i][j].length() > 3 && i % 3 == 0)
                    return true;
            </selection>
        }
        return false;
    }
}
