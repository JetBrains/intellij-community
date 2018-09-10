import java.util.Arrays;
import java.util.List;

class Main {

    public static void main(String...args){
        String[] array = new String[]{"a", "b", "c"};
        final List<String> strings = Arrays.asList(array);
        foo(strings);
    }

    private static void foo(List<String> anObject){
    }
}