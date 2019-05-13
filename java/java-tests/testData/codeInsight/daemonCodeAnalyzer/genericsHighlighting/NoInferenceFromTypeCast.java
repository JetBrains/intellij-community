import java.util.Collections;
import java.util.List;
import java.util.Set;


interface A {}

interface B extends A {}

class Foo {
    public <TA extends A> List<TA> getAs() {
        return (List<TA>) getBs();
    }

    public <T extends B> List<T> getBs() {
        return null;
    }

    void foo(Set<String> s) {}
    {
        foo(<error descr="Inconvertible types; cannot cast 'java.util.Set<java.lang.Object>' to 'java.util.Set<java.lang.String>'">(Set<String>)Collections.emptySet()</error>);
    }
}