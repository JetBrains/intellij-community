// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

public class Main {
    interface MyAutoCloseable {
        void close();
    }
    
    void test(List<MyAutoCloseable> list) {
        try(MyAutoCloseable ac = list<caret>.stream().filter(Objects::nonNull).findFirst().orElse(null)) {
            System.out.println(ac);
        }
    }
}