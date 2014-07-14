
import java.util.ArrayList;
import java.util.Collection;

public class ExtractVariableSample {

    public static void main(String[] args, String s) {
        Collection<String> strings = new ArrayList<>();
        new Object() {
            public void foo(String s) {
                System.out.println( s.hashCode());
            }
        };

        int j = s.hashCode();
        System.out.println(j);
    }


}