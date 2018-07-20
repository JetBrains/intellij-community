import java.util.Arrays;
import java.util.List;

class Main {

    public static void main(String...args){
        String[] array = new String[]{"a", "b", "c"};
        foo(array);
    }

    private static void foo(String... src){
        final List<String> str<caret>eam = Arrays.asList(src);
    }
}