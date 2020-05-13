import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

public class Test1 {
    record A(@F @M int value) {}

    void test(A a){
        a.<caret>value();
    }
}

@Target(value={FIELD})
@interface F{}

@Target(value={METHOD})
@interface M{}