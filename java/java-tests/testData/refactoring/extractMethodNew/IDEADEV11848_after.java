class Container {
    static class X {
        boolean x = false;

        void foo(String s, String t) {
            newMethod();

            newMethod();
        }

        private void newMethod() {
            x = true;
        }
    }
}