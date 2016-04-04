import java.util.LinkedList;
import java.util.List;

class MyTest {

  private Test<LinkedList> alist;


  public Test<LinkedList> getAlist() {
    return alist = create(Test::new, alist);
  }


  private <T> T create(CreateCallback<T> callback, T defaultVal) {
    if (defaultVal == null) {
      return callback.create();
    }
    return defaultVal;
  }

  interface CreateCallback<T> {
    T create();
  }

  class Test<E extends List> {}
}