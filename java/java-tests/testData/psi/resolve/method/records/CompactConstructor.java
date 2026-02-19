import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

record A(@P @M @T int value) {
    A {
        <caret>value
    }
}

@Target(value={PARAMETER})
@interface P{}

@Target(value={METHOD})
@interface M{}

@Target(value={TYPE_USE})
@interface T{}
