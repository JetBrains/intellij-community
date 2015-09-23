// "Remove 'unchecked' suppression" "false"
import java.util.*;

public class SampleSafeVarargs {

    @SafeVarargs
    static <T> List<T> asList(T... tt) {
        System.out.println(tt);
        return null;
    }

    @SuppressWarnings({"unchecked"})
    void fo<caret>o() {
        asList(new ArrayList<String>());
        List<Object> l ;
        ArrayList strings = new ArrayList<String>();
        l = strings;
    }
}
