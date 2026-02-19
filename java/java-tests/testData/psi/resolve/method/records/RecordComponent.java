import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

public class Test1 {
    record A(@F @M @T int @T1 [] value) {}

    void test(A a){
        a.<caret>value();
    }
}

@Target(value={FIELD})
@interface F{}

@Target(value={METHOD})
@interface M{}

@Target(value={TYPE_USE})
@interface T{}

@Target(value={TYPE_USE})
@interface T1{}