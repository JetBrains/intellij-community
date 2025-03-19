class C {
    void m(String arg) {
        if (arg != <caret>null && arg instanceof String) {
            System.out.println(arg);
        }
    }
}
