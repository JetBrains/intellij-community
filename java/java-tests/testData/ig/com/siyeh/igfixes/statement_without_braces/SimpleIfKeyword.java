class T {
    void f(String[] a) {
        <caret>if (a.length == 0)
            System.out.println("no");
    }
}