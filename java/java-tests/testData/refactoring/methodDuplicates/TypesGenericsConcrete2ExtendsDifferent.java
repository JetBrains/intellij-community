import java.util.ArrayList;
import java.util.List;

class Types {
    public void <caret>method(List<? extends ArrayList> v) {
        Object o = v.get(0);
    }
    public void context() {
        List<List> list = new ArrayList<List>();
        Object o = v.get(0);
    }
}
