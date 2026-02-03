import java.util.*;
public class Test {
    List<B> l;

    @Override
    Map<String, String> foo() {
        HashMap<String, String> m = new HashMap<String, String>();
        for (B b : l) {
            Map<String, String> map = b.foo();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!m.containsKey(entry.getKey())) {
                    m.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return m;
    }
}

class A {
    Map<String, String> foo(){return null;}
}

class B extends A {}