// "Add on demand static import for 'java.lang.annotation.ElementType'" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

@Target({//simple end comment
        METHOD})
@interface F {}