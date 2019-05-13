import java.util.Collections;
import java.util.Set;

class Test<Y> {

    public static <K> Test<K> doTest(K k){
        return null;
    }

    public static void main(String[] args) {
        Test.<Set<String>>doTest<error descr="'doTest(java.util.Set<java.lang.String>)' in 'Test' cannot be applied to '(java.util.Set<java.lang.Object>)'">(Collections.emptySet())</error>;
    }
}