
import java.util.ArrayList;
import java.util.Collection;

public class ExtractVariableSample {

    public static void main(String[] args) {
        Collection<String> strings = new ArrayList<>();
        new Object() {
            public void foo(String s) {
                System.out.println( s.hashCode());
            }
        };

        for (String s : strings) {
            int j = s.hashCode();
            System.out.println(j);
        }
    }


}