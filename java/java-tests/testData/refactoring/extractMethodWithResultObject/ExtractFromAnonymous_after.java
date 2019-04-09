import java.io.PrintStream;

class Test {
    public static void main() {
        new Runnable() {
            public void run() {
                newMethod().expressionResult.println("Text");
            }

            NewMethodResult newMethod() {
                return new NewMethodResult(System.out);
            }
        };
    }

    static class NewMethodResult {
        private PrintStream expressionResult;

        public NewMethodResult(PrintStream expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}