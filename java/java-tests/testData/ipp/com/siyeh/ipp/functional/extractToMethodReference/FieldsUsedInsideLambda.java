import java.util.List;

class MyClass {


  String myStr;
  void f(List<String> l){
    l.stream().filter(s -> //comment to keep
                        s.startsWith(my<caret>Str));
  }

}