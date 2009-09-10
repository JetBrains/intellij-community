import java.util.*;

class XX {
    public void g(List l) {
        for(Iterator <caret>it = l.iterator(); it.hasNext();) {
            Object o = it.next():
        }
    }
}