class C {
    String f () {
       return null;
    }

    C getParent() {return null;}

    void test () {
        C c = new C();
        for (;<selection>c.f()</selection> != null;) {
            c = c.getParent();
        }
    }
}