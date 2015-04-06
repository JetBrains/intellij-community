// "Replace lambda with method reference" "true"
import java.util.List;

class Test{
    private void example(List<String> list, String value)
    {
        list.stream().filter(value::equals);
    }
}
