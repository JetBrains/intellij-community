import java.util.*;

class ForeachNotReported {

  void f(List<String> <warning descr="Parameter 'list' can have 'final' modifier">list</warning>) {
    for (String s : list) {}
  }
}