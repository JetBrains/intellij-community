class C {
    Object o;

    void m() {
        o = new Object() {
            public String toString() {
                return "";
            }
        }
        o = new Object() {
            public String toString() {
                return "";
            }
        }
        System.out.println();
    }
}