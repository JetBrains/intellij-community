class Calculator {
    public void printError(Object detail, int line, String file) <caret>throws Exception {
        throw (Exception)detail;
    }
}
