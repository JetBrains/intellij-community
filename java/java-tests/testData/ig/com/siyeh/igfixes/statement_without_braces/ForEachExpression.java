class T {
    void f(String[] a) {
        for (String s : <caret>a)
            System.out.println(s);
    }
}