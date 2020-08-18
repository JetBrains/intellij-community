// "Replace 'var' with explicit type" "true"
import java.lang.annotation.*;
class Main {
    {
        @An<caret>no var b = "hello";
    }
}

@Target(ElementType.TYPE_USE)
@interface Anno {}