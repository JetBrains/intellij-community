// "Create parameter 'popop'" "true-preview"
class Calculator {
    public void printError(String detail, int line, String file, String popop) {
        int i = popop;
    }
}
class SSS extends Calculator {
    public void printError(String detail, int line, String file, String popop) {
        String s = <caret>popop;
    }
}
