
import java.util.ArrayList;
import java.util.Collection;

public class ExtractVariableSample {
    interface I {
      void foo(String s);
    }

    public static void main(String[] args) {
        Collection<String> strings = new ArrayList<>();
        I i = (s) -> {
            int j = s.hashCode();
            System.out.println(j); };

        for (String s : strings) {
            System.out.println(s.hashCode());
        }
    }


}