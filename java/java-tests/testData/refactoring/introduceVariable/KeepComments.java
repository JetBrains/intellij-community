import java.util.Arrays;
import java.util.List;

class Main {


    private static void foo(String... src){
        <selection>Arrays.asList(src)
                //c-2
                .toArray()//c-1
                .toString()</selection>
        //c1
        //c2
        ;
    }
}