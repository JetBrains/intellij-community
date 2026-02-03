import java.util.function.Predicate;

class Test {
    Predicate<String> p = obj -> obj != null && !<selection>obj.trim()</selection>.isEmpty();
}
