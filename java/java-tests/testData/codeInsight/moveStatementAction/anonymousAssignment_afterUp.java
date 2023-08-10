class C {
    Object o;

    void m() {
        System.out.println();
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
    }
}