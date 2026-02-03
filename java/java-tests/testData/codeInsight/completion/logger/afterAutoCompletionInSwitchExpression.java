import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A {
    private static final Logger log = LoggerFactory.getLogger(A.class);

    public static void main(String[] args) {
        String x = "";
        switch(x) {
            case "name" -> log<caret>
            default -> "String";
        };
    }
}