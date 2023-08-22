class T {
    void f(String[] a) {
        int k = 0;
        while <caret>(k < a.length)
            System.out.println(a[k++]);
    }
}