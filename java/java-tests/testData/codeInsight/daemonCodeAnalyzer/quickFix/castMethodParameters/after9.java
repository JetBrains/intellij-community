// "Cast 1st argument to 'Throwable'" "true-preview"
class a {
    void f(Throwable a, Throwable b) {}
    void g() {
        Exception e=null;
        Object o = null;
        f((Throwable) e,o);
    }
}

