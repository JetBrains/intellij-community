 class Cast {
    public void f(Object o1, String ... o) {

    }

    public void f(String s, Integer o) {

    }

    public void g() {
// Code is green - but we need a cast here
        <ref>f(null, null);
    }
}