class A {

    void m() {
        new Object() {}
        <caret>System.out.println();
        new Object() {}
    }
}