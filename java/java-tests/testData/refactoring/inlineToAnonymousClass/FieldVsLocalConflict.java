class A {
    public void doTest() {
        Throwable t = new Throwable("q");
        Object b = new Inner(new Throwable("t"));
    }

    private class <caret>Inner {
        private String myMessage;

        public Inner(Throwable t) {
            String msg = t.getMessage();
            myMessage = msg;
        }
    
        public String toString() {
            return "A";
        }
    }
}