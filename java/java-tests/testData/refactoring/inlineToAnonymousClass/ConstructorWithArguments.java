class A {
    private Object b = new MyException("w");

    private class <caret>MyException extends Exception {
        public MyException(String msg) {
            super(msg);
        }

        public String getMessage() {
            return "q";
        }
    }
}