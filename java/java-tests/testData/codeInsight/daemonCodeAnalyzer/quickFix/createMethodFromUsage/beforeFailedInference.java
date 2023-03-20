// "Create method 'list'" "true-preview"
import java.util.List;

class MainService {
}

class MainController {
  private final MainService service = new MainService();
  public Wrapper<List<Data>> listData() {
    return Wrapper.wrap(service.<caret>list());
  }
}

class Wrapper<T> {
  private T data;
  public static <T> Wrapper<T> wrap(T t) {
    Wrapper<T> res = new Wrapper<>();
    res.data = t;
    return res;
  }
}

interface Data {}