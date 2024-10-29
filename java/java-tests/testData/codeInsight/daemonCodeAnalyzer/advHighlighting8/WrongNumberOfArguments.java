import java.util.function.Consumer;

class WrongNumberOfArguments {

  void fun1(String str, Consumer<String> onFinish) { }
  void getStr(String asd) { }

  void asd() {
    int asd = 1;
    fun1("text", s -> getStr(s), <error descr="Expected 2 arguments but found 3">asd</error>);
  }
}