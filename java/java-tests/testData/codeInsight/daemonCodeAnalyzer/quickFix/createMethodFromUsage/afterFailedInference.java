// "Create method 'list'" "true-preview"
import java.util.List;

class MainService {
    public List<Data> list() {
        <selection><caret>return null;</selection>
    }
}

class MainController {
  private final MainService service = new MainService();
  public Wrapper<List<Data>> listData() {
    return Wrapper.wrap(service.list());
  }
}

class Wrapper<T> {
  private T data;
  public static <T1> Wrapper<T1> wrap(T1 t) {
    Wrapper<T1> res = new Wrapper<>();
    res.data = t;
    return res;
  }
}

interface Data {}