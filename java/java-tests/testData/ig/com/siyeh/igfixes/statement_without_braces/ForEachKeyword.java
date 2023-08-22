class T {
    void f(String[] a) {
        <caret>for (String s : a)
            System.out.println(s);
    }
}