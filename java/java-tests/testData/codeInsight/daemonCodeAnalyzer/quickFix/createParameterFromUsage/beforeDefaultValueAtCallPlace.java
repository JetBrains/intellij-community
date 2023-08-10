// "Create parameter 'popop'" "true-preview"
class Calculator {
    public void printError() {
        int i = <caret>popop;
    }

    {
        printError();
    }
}
