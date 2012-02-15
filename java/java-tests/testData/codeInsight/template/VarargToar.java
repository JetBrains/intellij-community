import java.util.List;

public class LiveTemplateTest {

  void foo(String... messages){}
  void usage(){
    List<String> messages = new ArrayList<String>();
    foo(<caret>);
  }

}