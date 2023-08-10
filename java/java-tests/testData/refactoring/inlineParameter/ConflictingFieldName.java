class A {
    private String f;

    public void m() {
        m1(f);
    }

    private void m1(String <caret>f) {
        if (!f.isEmpty()) {
            System.out.println(f);
        }
    }
}