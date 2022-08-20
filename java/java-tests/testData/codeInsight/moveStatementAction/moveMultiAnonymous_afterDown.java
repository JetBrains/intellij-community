class A {

    void m() {
        new Object() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };
        new Object() {
            @Override
            public int hashCode() {
                 return super.hashCode();
            }
        };
        System.out.println();
        System.out.println();
    }
}