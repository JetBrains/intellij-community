class A {
    private Object b = new MyException("w");

    private class <caret>MyException extends Exception {
        public MyException(String msg) {
            this(new Throwable(), msg);
        }

        public MyException(Throwable t, String msg) {
            super(msg, t);
        }

        public String getMessage() {
            return "q";
        }
    }
}