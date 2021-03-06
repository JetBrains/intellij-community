// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

public class Main {
    interface MyAutoCloseable {
        void close();
    }
    
    void test(List<MyAutoCloseable> list) {
        MyAutoCloseable found = null;
        for (MyAutoCloseable myAutoCloseable : list) {
            if (myAutoCloseable != null) {
                found = myAutoCloseable;
                break;
            }
        }
        try(MyAutoCloseable ac = found) {
            System.out.println(ac);
        }
    }
}