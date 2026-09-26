class Bug {
    static A test(A[] as) {
        for (<error descr="Incompatible types. Found: 'Bug.B', required: 'Bug.A'">B b</error> : as) {
            return <error descr="Incompatible types. Found: 'Bug.B', required: 'Bug.A'">b</error>;
        }
        return null;
    }
    static class A {}
    static class B {}
}