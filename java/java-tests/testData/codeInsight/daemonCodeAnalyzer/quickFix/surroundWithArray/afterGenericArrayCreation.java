// "Surround with array initialization" "true"
import java.util.List;

class A {

  public List<?>[] test(List<Number> list) {
    return new List[]{list};
  }
}