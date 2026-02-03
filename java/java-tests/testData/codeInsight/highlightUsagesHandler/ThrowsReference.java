class Calculator {
    public void printError(Exception detail, int line, String file) <caret>throws Exception {
        throw detail;
    }
}
