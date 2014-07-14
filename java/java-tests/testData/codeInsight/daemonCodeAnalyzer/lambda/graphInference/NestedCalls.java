import java.util.*;
class Main {
    public static <T> T foo() {return null;}
    
    public static <B> List<B> bar(B b) {return null;}
    static {
        List<String> s =  bar(foo());
    }
    

    public static <B> B bar1(B b) {return null;}
    static {
        String s1 =  bar1(foo());
    }
}
