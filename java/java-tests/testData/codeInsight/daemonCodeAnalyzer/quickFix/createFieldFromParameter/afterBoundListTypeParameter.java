// "Create Field For Parameter 'p1'" "true"

import java.util.*;
class Test{
    private final List<String> myP1;

    <T extends String> void f(List<T> p1){
        myP1 = p1;
    }
}

