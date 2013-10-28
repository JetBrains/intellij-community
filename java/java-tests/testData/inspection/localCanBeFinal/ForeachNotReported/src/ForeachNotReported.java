import java.util.*;

class ForeachNotReported {

  void f(List<String> list) {
    for (String s : list) {}
  }
}