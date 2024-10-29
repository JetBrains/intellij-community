import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A {
    private static final Logger log = LoggerFactory.getLogger(A.class);

    void foo() {
        for (int i = 0; i < 1; ++i) log<caret>
    }
}