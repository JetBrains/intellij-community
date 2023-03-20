import java.util.Formatter;

public class Test {

    String message;
    static String staticField = "Smt";

    public void run() {
        extracted(message);
    }

    private static void extracted(String message) {
        local();
        System.out.println(staticField);
        System.out.println(message);
    }

    private static void local(){}
}