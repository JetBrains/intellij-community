import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class A {
    private static final Logger log = LoggerFactory.getLogger(A.class);

    void foo() {
        Consumer<String> s = (param) -> log<caret>
    }
}