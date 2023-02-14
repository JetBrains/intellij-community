import java.util.Formatter;

public class Test {

    String message;

    public void run() {
        extracted();
    }

    private void extracted() {
        local();
        System.out.println(message);
    }

    private void local(){}
}