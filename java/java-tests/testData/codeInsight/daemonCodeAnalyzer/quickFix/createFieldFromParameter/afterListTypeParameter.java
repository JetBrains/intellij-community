// "Create Field for Parameter 'p1'" "true"

import java.util.*;
class Test{
    private List<Object> myP1;

    <T> void f(List<T> p1){
        myP1 = p1;
    }
}

