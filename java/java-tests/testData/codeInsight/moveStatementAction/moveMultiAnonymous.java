class A {

    void m() {
        new Object() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };
        <selection><caret>System.out.println();
        System.out.println();</selection>
        new Object() {
            @Override
            public int hashCode() {
                 return super.hashCode();
            }
        };
    }
}