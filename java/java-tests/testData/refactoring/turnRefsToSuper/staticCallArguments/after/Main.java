import java.util.*;
interface Int {}
class Impl implements Int {
}

class Usage {
    void f(List<Int> l){}
    void bar() {
        f(Collections.<Int>emptyList());
    }
}
