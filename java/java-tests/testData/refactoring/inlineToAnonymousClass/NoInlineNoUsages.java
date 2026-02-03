class A {
    private class <caret>Inner {
        public Inner newInstance() {
            return new Inner();
        }
    }
}