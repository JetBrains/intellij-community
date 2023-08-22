// "Add explicit type arguments" "true-preview"
import java.util.Collections;
import java.util.List;

class Bar {

  public static void main(String[] args) {
    new Bar().complexTypeParams(Collections.<Class<?>>emptyList());
  }

  void complexTypeParams(List<Class<?>> list) {}
}



