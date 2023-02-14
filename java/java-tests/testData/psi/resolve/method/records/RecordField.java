import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

record A(@F @M @T int value) {
    void test(){
        <caret>value
    }
}

@Target(value={FIELD})
@interface F{}

@Target(value={METHOD})
@interface M{}

@Target(value={TYPE_USE})
@interface T{}
