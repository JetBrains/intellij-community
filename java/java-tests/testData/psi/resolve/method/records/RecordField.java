import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

record A(@F @M int value) {
    void test(){
        <caret>value
    }
}

@Target(value={FIELD})
@interface F{}

@Target(value={METHOD})
@interface M{}
