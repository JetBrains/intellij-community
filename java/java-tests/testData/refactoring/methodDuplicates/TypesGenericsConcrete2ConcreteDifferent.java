import java.util.List;

class Types {
  private List<Boolean> myList;

  public void <caret>method(List<Integer> v) {
    v.clear();
  }

  public void context() {
    myList.clear();
  }
}
