// "Replace 'var' with explicit type" "true-preview"
import java.lang.annotation.*;
class Main {
    {
        @Anno String b = "hello";
    }
}

@Target(ElementType.TYPE_USE)
@interface Anno {}