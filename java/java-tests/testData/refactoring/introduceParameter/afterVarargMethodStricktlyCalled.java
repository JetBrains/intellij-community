import java.util.Arrays;
import java.util.List;

class Main {

    public static void main(String...args){
        String[] array = new String[]{"a", "b", "c"};
        final List<String> list = Arrays.asList(array);
        foo(list);
    }

    private static void foo(List<String> anObject){
    }
}