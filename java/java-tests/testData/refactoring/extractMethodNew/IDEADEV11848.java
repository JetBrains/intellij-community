class Container {
    static class X {
        boolean x = false;

        void foo(String s, String t) {
            <selection>x = true;</selection>

            x = true;
        }
    }
}