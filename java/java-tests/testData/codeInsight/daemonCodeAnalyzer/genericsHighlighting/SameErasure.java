import java.util.*;

class SameSignatureTest {
    <error descr="'sameErasure(List<String>)' clashes with 'sameErasure(List<Integer>)'; both methods have same erasure">public static void sameErasure(List<String> strings)</error> {
    }

    public static void sameErasure(List<Integer> integers) {
    }
}

 class CCC {
    <error descr="'f(Object)' clashes with 'f(Object)'; both methods have same erasure"><T> void f(Object o)</error> {}

    void f(Object o) {}
}

public class Test1 {
    <error descr="'bug(String)' clashes with 'bug(String)'; both methods have same erasure">public void bug(String s)</error> {
    }

    public static <T> T bug(String s) {
        return null;
    }
}
////////////////////////////////
class Test {
    <error descr="'test()' clashes with 'test()'; both methods have same erasure">public static <K, V> HashMap<K, V> test()</error> {
        return new HashMap<K, V>();
    }

    public static String test() {
        return "";
    }
}
