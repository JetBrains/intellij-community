class C {
    void m() {
        System.out.println();
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
    }
}