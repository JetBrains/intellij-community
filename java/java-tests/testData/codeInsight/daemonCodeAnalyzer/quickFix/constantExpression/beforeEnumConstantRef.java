// "Fix all 'Constant expression can be evaluated' problems in file" "false"

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(Retention<caret>Policy.RUNTIME)
@interface Ann {}