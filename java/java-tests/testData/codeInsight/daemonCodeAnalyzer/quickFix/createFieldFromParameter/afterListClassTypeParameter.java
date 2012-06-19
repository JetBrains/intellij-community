// "Create Field for Parameter 'p1'" "true"

import java.util.*;
class Test<T>{
    private List<T> myP1;

    void f(List<T> p1){
        myP1 = p1;
    }
}

