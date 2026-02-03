// "Create field for parameter 'p1'" "true-preview"

import java.util.*;
class Test{
    private List<?> myP1;

    <T> void f(List<T> p1){
        myP1 = p1;
    }
}

