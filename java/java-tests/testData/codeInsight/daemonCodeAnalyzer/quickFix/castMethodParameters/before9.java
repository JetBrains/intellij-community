// "Cast 1st parameter to 'java.lang.Throwable'" "false"
class a {
    void f(Throwable a, Throwable b) {}
    void g() {
        Exception e=null;
        Object o = null;
        f(<caret>e,o);
    }
}

