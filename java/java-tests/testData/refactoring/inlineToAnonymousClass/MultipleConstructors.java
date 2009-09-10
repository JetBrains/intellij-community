class A {
    private Object b = new MyException(new Throwable(), "w");

    private class <caret>MyException extends Exception {
        public MyException(String msg) {
            super(msg);
        }

        public MyException(Throwable t, String msg) {
            super(msg, t);
        }

        public String getMessage() {
            return "q";
        }
    }
}