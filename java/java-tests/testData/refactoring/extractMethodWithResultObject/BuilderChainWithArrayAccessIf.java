class Foo {
    boolean bar(String[] a) {
        for (int i = 0; i < a.length; i++) {<selection>
            if (a[i].length() > 3 && i % 3 == 0)
                return true;
        </selection>}
        return false;
    }
}
