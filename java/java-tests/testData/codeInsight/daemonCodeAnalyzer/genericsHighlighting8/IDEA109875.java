import java.util.Collections;
import java.util.Set;

class Test<Y> {

    public static <K> Test<K> doTest(K k){
        return null;
    }

    public static void main(String[] args) {
        Test.<Set<String>>doTest(Collections.emptySet());
    }
}
