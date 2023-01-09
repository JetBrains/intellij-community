import java.util.Formatter;

public class Test {

    String message;

    public void run() {
        extracted(this, message);
    }

    private static void extracted(Test test, String message) {
        Formatter formatter = new Formatter();
        formatter.format("%s", test);
        System.out.println(message);
    }
}