import java.util.Arrays;
import java.util.List;

class Main {


    private static void foo(String... src){
        String m = Arrays.asList(src)
                //c-2
                .toArray()//c-1
                .toString();//c1
//c2
    }
}