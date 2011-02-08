class A {}
class B extends A{}
class Cast {
    public void f(A... o) {

    }

    public void f(A o) {

    }

    public void g() {
// Code is green - but we need a cast here
        <ref>f(null);
    }
}