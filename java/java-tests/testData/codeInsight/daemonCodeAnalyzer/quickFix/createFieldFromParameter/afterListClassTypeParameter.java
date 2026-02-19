// "Create field for parameter 'p1'" "true-preview"

import java.util.*;
class Test<T>{
    private List<T> myP1;

    void f(List<T> p1){
        myP1 = p1;
    }
}

