class C {
    String f () {
       return null;
    }

    C getParent() {return null;}

    void test () {
        C c = new C();
        String wrong = c.f();
        for (; wrong != null;) {
            c = c.getParent();
        }
    }
}