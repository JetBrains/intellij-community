class C {
    void m() {
        new Object() {
            public String toString() {
                return null;
            }
        }.toString();
        new Object() {
            public String toString() {
                return null;
            }
        }.toString();
        System.out.println();
    }
}