import java.util.*;

class Test {
     <E extends A> List<E> met<caret>hod() {
        return new ArrayList<E>();
    }

    void duplicated() {
        List<B> l = new ArrayList<B>();
    }

    class A {}
    class B extends A {}
}