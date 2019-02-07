import java.util.function.Predicate;

class Test {
    Predicate<String> p = obj -> {
        if (obj == null) return false;
        String temp = obj.trim();
        return !temp.isEmpty();
    };
}
