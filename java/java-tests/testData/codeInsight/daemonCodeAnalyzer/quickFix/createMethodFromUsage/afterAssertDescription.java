// "Create method 'f'" "true"
class A {
    {
         assert false: f();
    }

    private String f() {
        <selection>return null;</selection>
    }
}
