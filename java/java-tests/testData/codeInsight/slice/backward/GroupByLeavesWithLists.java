import java.util.ArrayList;
import java.util.List;

class ListTack {

    void f(String s) {
        String <caret>p = s;
    }
    void g(List<String> list) {
        list.add(1, "uuu");

        f(list.get(0));
        f(list.remove(0));
    }
    void h() {
        List<String> g = new ArrayList<String>();
        g.add("zzz");
        List<String> list = new ArrayList<String>();
        list.add("xxx");
        g(list);
    }

}
