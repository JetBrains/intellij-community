class T {
    void f(String[] a) {
        int j = 0;
        do System.out.println<caret>(a[j++]);
        while (j < a.length);
    }
}