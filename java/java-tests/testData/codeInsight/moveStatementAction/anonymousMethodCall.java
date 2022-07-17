class C {
    void m() {
        new Object() {
            public String toString() {
                return null;
            }
        }.toString();
        <caret>System.out.println();
        new Object() {
            public String toString() {
                return null;
            }
        }.toString();
    }
}