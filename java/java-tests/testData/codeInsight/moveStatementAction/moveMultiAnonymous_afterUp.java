class A {

    void m() {
        System.out.println();
        System.out.println();
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
    }
}