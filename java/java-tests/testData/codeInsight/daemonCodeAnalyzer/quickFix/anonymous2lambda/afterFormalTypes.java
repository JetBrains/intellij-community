// "Replace with lambda" "true"
import java.util.*;
class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
 
    {
        bar((I<String>) list -> null);
    }
}
