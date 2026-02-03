class A {
    private Object b = new MyException("w");

    private class <caret>MyException extends Exception {
        public MyException(String msg) {
            super(msg.substring(0, 1));
        }

        public String getMessage() {
            return "q";
        }
    }
}