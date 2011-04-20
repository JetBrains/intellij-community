class Neg09 {
    class Member<X> {}

    static class Nested<X> {}

    void testSimple() {
        Member<?> m1 = new Member<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>() {};
        Nested<?> m2 = new Nested<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>() {};
    }

    void testQualified() {
        Member<?> m1 = this.new Member<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>() {};
        Nested<?> m2 = new Neg09.Nested<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>() {};
    }
}
