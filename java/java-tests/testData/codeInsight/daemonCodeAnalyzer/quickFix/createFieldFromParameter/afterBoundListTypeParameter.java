// "Create field for parameter 'p1'" "true-preview"

import java.util.*;
class Test{
    private List<? extends String> myP1;

    <T extends String> void f(List<T> p1){
        myP1 = p1;
    }
}

