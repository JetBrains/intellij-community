import java.util.*;

class LambdaTest {
    public void testR() {
        <error descr="Incompatible types. Found: 'java.lang.String', required: '<method reference>'">new ArrayList<String>() :: size = ""</error>; 

    }
}