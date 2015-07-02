// "Annotate as @SafeVarargs" "true"
import java.util.List;
public class Test {
    public <T> @SafeVarargs
    static void main(List<T>... args) {

    }
}

