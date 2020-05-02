class DeclaredOutputVariable {
    void foo(String[] a, int j) {
        <selection>
        if (a[j] == null) return;
        String s = a[j];
        System.out.println(s.charAt(1) + "X");
        </selection>
        System.out.println(s.length());
    }

    void bar(String[] a, int k) {
        if (a[k] == null) return;
        String s = a[k];
        System.out.println(s.charAt(2) + "Y");
        System.out.println(s.length());
    }
}