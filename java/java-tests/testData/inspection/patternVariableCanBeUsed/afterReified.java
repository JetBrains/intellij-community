// "Replace 'list' with pattern variable" "true"
import java.util.List;

class X {
  void test(Object obj) {
    if (obj instanceof List<?> list) {
    }
  }
}