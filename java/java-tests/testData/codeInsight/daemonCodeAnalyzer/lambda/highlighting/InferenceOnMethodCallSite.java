import java.util.*;
interface In<X> {
    List<X> f();
}

class InferenceOnMethodCallSite {
    <Z> void m(In<Z> i, In<Z> ii) { }
    <Z> void m(In<Z> s) { }

    {
        m(<error descr="Cyclic inference">( ) -> Collections.emptyList()</error>);
        m((In<String>)() -> Collections.emptyList(), () ->  new ArrayList<String>());
        m(() ->Collections.<String>emptyList(), () -> new ArrayList<String>());
        m(() -> Collections.<String>emptyList());
    }
    
}
