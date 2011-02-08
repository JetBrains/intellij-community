class C {
    class Inner {}
    <T extends Inner> T f() {return null;}

    void bar () {
        Object o = <ref>f();
    }
}