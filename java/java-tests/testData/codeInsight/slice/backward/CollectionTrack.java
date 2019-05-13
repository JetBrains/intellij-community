package x;
import java.util.*;
import org.intellij.lang.annotations.Flow;

class ListTack {
    void f(String <caret>s) {
    }
    void g(List<String> l) {
        l.add(1, "uuu");

        f(l.get(0));
    }
    void h() {
        ArrayList<String> strings = new ArrayList<String>();
        strings.add("x");
        X<String> s2 = new X<String>(strings);
        s2.add("y");
        List<String> s3 = new ArrayList<String>();
        s3.addAll(s2.toCollection());

        Collection<String> s4 = new ArrayList<String>(s3.subList(0,1));
        g(new ArrayList<String>(s4));
    }

  class X<T> {
    X (@Flow(sourceIsContainer = true, targetIsContainer = true) Collection<T> input) {}

    @Flow(sourceIsContainer = true)
    T get() { return null;}

    void add(@Flow(targetIsContainer = true) T item) {}

    @Flow(sourceIsContainer=true, targetIsContainer = true)
    Collection<T> toCollection() { return null; }
  }
}

