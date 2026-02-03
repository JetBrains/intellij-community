class C {
    String f () {
       return null;
    }

    C getParent() {return null;}

    void test () {
        C c = new C();
        while (<selection>c.f()</selection> != null) {
            c = c.getParent();
        }
    }
}