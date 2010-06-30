// "Create Field For Parameter 'p1'" "true"

import java.util.*;
class Test<T>{
    private final List<T> myP1;

    void f(List<T> p1){
        myP1 = p1;
    }
}

