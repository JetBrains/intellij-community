class Test {
    boolean <caret>fie;
    int f(T t) {
        fie = t.t;
        return 0;
    }

    void g(T t) {
//        t.t = false;
        fie = true;
    }
    void h(T t) {
        boolean t1 = t.t;
        try {
            t.t = false;
            g(t);
        }
        finally {
            t.t = t1;
        }
    }


    private class T {
        public boolean t = true;

        public boolean isT() {
            isT();
            return t;
        }
    }
}
