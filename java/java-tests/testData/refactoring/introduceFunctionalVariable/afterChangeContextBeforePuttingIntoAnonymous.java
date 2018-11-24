import java.util.function.Function;

class Test {
    String string;
    void useThis() {
        Function<String, String> stringStringFunction = string -> string;
        System.out.println(stringStringFunction.apply(string));
    }
}