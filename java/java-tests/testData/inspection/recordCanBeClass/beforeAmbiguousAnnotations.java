// "Convert record to class" "true-preview"
import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE_USE})
@interface Anno {}

record <caret>R(@Anno int f) {}
