// "Cast 2nd argument to 'Throwable'" "true-preview"
class a {
    void f(Throwable a, Throwable b) {}
    void g() {
        Exception e=null;
        Object o = null;
        f(<caret>e, (Throwable) o);
    }
}

