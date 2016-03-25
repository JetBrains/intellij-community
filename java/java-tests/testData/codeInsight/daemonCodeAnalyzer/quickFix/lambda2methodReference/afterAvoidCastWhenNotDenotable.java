// "Replace lambda with method reference" "true"
import java.util.ArrayList;
import java.util.List;

class Test {
  {
    List<?> list = new ArrayList<>();
    list.stream().map(Object::toString);
  }
}