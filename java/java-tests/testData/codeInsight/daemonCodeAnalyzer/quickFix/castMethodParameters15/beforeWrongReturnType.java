// "Cast parameter to 'java.util.List<A>'" "false"

import java.util.*;

class A { }

class B extends A { }

class C {
    public B getStrings(ArrayList<A> l) {
        return getFi<caret>rstItem(l);
    }

    private static <T> T getFirstItem(List<T> items) { return null; }
    private static <T> T getFirstItem(Collection<T> items) { return null; }
}
