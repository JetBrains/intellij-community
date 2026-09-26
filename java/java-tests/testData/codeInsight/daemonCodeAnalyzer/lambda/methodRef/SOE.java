import java.util.*;

class LambdaTest {
    public void testR() {
        <error descr="Reference to variable expected on left-hand side of assignment">new ArrayList<String>() :: size</error> = ""; 

    }
}