 class Cast {
    public void f(String ... o) {

    }

    public void f(Integer o) {

    }

    public void g() {
// Code is green - but we need a cast here
        <ref>f(null);
    }
}