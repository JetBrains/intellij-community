import java.util.*;

class LambdaTest {
    public void testR() {
        new ArrayList<String>() :: <error descr="Cannot resolve method 'size'">size</error> = ""; 

    }
}