import java.util.Map;

public class IgnoreSuspicious {
    Map<String, String> map;

    void f(Object s){
        String str = (String) s;
        map.remove((String)s);
        map.remove((<warning descr="Casting 'str' to 'String' is redundant">String</warning>)str);
    }

}