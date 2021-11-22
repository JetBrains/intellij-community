// "Cast 1st argument to 'Throwable'" "true"
class a {
    void f(Throwable a, Throwable b) {}
    void g() {
        Exception e=null;
        Object o = null;
        f(<caret>e,o);
    }
}

