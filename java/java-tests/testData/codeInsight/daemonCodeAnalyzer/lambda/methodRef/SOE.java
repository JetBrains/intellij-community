import java.util.*;

class LambdaTest {
    public void testR() {
        new ArrayList<String>() :: <error descr="Reference to 'size' is ambiguous, both 'size()' and 'size()' match">size</error> = ""; 

    }
}