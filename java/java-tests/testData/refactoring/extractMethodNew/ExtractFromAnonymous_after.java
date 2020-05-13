import java.io.PrintStream;

class Test {
    public static void main() {
        new Runnable() {
            public void run() {
                newMethod().println("Text");
            }
        }
    }

    private static PrintStream newMethod() {
        return System.out;
    }
}