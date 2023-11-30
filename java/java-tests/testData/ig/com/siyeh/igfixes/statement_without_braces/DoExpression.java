class T {
    void f(String[] a) {
        int j = 0;
        do System.out.println(a[j++]);
        while (j < a<caret>.length);
    }
}