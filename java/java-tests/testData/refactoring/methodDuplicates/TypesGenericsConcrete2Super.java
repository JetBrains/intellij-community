import java.util.ArrayList;
import java.util.List;

class Types {
    public void <caret>method(List<? super JComponent> v) {
        Object o = v.get(0);
    }
    public void context() {
        List<Component> list = new ArrayList<Component>();
        Object o = list.get(0);
    }
}
class JComponent extends Component {}
class Component {}