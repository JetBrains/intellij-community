import java.util.List;

class X {
  void foo(List<String> list){
    Runnable c = () -> {
        list.add("");
    };
  }
}