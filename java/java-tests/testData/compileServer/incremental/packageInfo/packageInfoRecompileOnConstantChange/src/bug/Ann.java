package bug;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Retention(RUNTIME) @Target({PACKAGE})
public @interface Ann {
    String namespace() default "";
}