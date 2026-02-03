import java.util.ArrayList;
import java.util.List;

class Types {
    public void <caret>method(List<? extends List> v) {
        int i = v.get(0).getX();
    }
    public void context() {
        List<ArrayList> list = new ArrayList<ArrayList>();
        int j = list.get(0).getX();
    }
}
