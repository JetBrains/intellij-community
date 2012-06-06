import java.util.*;
interface Int {}
class Impl implements Int {
    void foo(){}
}

class Usage {
    void f(List<Impl> l){
        for (Impl aImpl : l) {
            aImpl.foo();
        }
        l.get(0).foo();
    }
}
