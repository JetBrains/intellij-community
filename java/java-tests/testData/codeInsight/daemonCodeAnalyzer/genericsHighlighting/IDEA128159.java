class Bug {
    static A <error descr="Invalid return type">test</error>(A[] as) {
        for (<error descr="Incompatible types. Found: 'Bug.B', required: 'Bug.A'">B b</error> : as) {
            <error descr="Incompatible types. Found: 'Bug.B', required: 'Bug.A'">return b;</error>
        }
        return null;
    }
    static class A {}
    static class B {}
}