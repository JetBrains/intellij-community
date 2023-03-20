class C {
    Object o;

    void m() {
        o = new Object() {
            public String toString() {
                return "";
            }
        }
        <caret>System.out.println();
        o = new Object() {
            public String toString() {
                return "";
            }
        }
    }
}