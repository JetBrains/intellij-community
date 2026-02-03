class Calculator {
    public void printError(String detail, int line, String file) <caret>throws Exception {
        throw new Exception();
    }
}
