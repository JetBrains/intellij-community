class DeclaredOutputVariable {
    void foo(String[] a, int j) {
        <selection>
        String s = a[j];
        if (s == null) return;
        System.out.println(s.charAt(1) + "X");
        </selection>
        System.out.println(s.length());
    }

    void bar(String[] a, int k) {
        String s = a[k];
        if (s == null) return;
        System.out.println(s.charAt(2) + "Y");
        System.out.println(s.length());
    }
}