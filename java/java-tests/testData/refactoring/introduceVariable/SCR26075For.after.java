class C {
    String f () {
       return null;
    }

    C getParent() {return null;}

    void test () {
        C c = new C();
        for (;c.f() != null;) {
            c = c.getParent();
        }
    }
}