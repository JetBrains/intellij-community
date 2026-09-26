import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

record A(@F @M int value) {
    int value() {
        return 0;
    }

    void test() {
        <caret>value();
    }
}