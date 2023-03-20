// "Create method 'f'" "true-preview"
class A {
    {
         assert false: f();
    }

    private String f() {
        <selection>return null;</selection>
    }
}
