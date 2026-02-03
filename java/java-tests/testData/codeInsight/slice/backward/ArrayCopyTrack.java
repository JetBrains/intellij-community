class ArrayCopy {

    void f(String s) {
        String[]  l = new String[]{"x"};
        String[] y = new String[1];
        System.arraycopy(l, 0, y, 0, l.length);
        String <caret>c = y[0];
    }
}
