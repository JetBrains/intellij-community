class A {
    private Object b = new MyException();

    private class <caret>MyException extends Exception {
        public MyException() {
            super("w");
        }

        public String getMessage() {
            return "q";
        }
    }
}