import java.util.List;

// IDEA-318996
public class BoxedBooleanNullableTrue {
  protected void doSomething(String s, List<Boolean> list) {
    Boolean value = checkString(s) ? true : null;
    if (list != null) {
      list.add(value);
    }
  }

  private boolean checkString(String s) {
    return s.equals("la la la");
  }
}