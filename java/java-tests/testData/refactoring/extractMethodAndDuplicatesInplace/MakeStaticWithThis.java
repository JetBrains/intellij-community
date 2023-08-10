import java.util.Formatter;

public class Test {

    String message;

    public void run() {
        <selection>Formatter formatter = new Formatter();
        formatter.format("%s", this);
        System.out.println(message);</selection>
    }
}