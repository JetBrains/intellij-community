class A {
    private Object b = new Inner();

    private class <caret>Inner {
        public Inner() {
            // this does some stuff
            doStuff();
            /* isn't this interesting? */
        }

        public String doStuff() {
            return "A";
        }
    }
}