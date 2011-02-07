import java.util.*;

class Thing {
}
class Testergen {
    <T extends Thing> T x(T thing) {
        return null;
    }

    <T extends Thing> T x(Collection<T> thing) {
        return null;
    }
}
class TestergenUser {
    public void context(Testergen test) {
        // the error is shown when x is called:
        Collection<Thing> t = null;
        test.<ref>x(t);
    }

}

