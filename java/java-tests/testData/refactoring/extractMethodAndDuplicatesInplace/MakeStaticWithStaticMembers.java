import java.util.Formatter;

public class Test {

    String message;
    static String staticField = "Smt";

    public void run() {
        <selection>local();
        System.out.println(staticField);
        System.out.println(message);</selection>
    }

    private static void local(){}
}