import java.util.*;
interface Int {}
class Impl implements Int {
}

class Usage {
    void f(List<Impl> l){}
    void bar() {
        f(Collections.<Impl>emptyList());
    }
}
