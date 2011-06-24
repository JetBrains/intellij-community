import java.util.Map;

public class Suspicious {
    Map<String, String> map;

    void f(Object s){
        String str = (String) s;
        map.remove((String)s);
        map.remove((String)str);
    }

}