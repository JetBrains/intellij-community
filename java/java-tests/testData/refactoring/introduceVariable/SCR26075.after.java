class C {
    String f () {
       return null;
    }

    C getParent() {return null;}

    void test () {
        C c = new C();
        while (true) {
            String ok = c.f();
            if (!(ok != null)) break;
            c = c.getParent();
        }
    }
}