// "Create method 'list'" "true-preview"
import java.util.*;

class MainService {
}

class MainController {
  private final MainService service = new MainService();
  public Wrapper<Map<String, Optional<Data>>> listData() {
    return Wrapper.wrap(service.<caret>list());
  }
}

class Wrapper<T> {
  private T data;
  public static native <T1, T2> Wrapper<Map<T1, Optional<T2>>> wrap(Map<T1, T2> t);
}

interface Data {}