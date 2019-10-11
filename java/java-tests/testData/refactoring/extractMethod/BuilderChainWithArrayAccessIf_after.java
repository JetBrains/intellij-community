class Foo {
    boolean bar(String[] a) {
        for (int i = 0; i < a.length; i++) {
            if (newMethod(a, i)) return true;
        }
        return false;
    }

    private boolean newMethod(String[] a, int i) {
        if (a[i].length() > 3 && i % 3 == 0)
            return true;
        return false;
    }
}
