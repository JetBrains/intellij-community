class T {
    void f(String[] a) {
        int k = 0;<caret>
        while (k < a.length)
            System.out.println(a[k++]);
    }
}