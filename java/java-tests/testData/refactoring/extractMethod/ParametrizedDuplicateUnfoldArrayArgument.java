class DeclaredOutputVariable {
    void foo(String[] a) {
        <selection>
        String s = a[1];
        if (s == null) return;
        System.out.println(s.charAt(1));
        </selection>
        System.out.println(s.length());
    }

    void bar(String[] a) {
        String s = a[2];
        if (s == null) return;
        System.out.println(s.charAt(2));
        System.out.println(s.length());
    }
}