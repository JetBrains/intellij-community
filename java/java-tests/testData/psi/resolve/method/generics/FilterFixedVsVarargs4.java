class A {}
class B extends A{}
class Cast {
    public void f(A... o) {

    }

    public void f(A o) {

    }

    public void g() {
// Casts ensures correct candidates filtering
        <ref>f((A) null);
    }
}