// "Remove 'CodeBlock2Expr' suppression" "false"
import java.util.*;

interface I {
    int m();
}
public class SampleSafeVarargs {

    {
        I i = () -> {
            //noinspection CodeBl<caret>ock2Expr
           return foo();
        };
    }

    int foo() {
        return 1;
    }
}
