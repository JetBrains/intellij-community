import java.lang.String;
import java.util.List;

public class LiveTemplateTest {

  void usage(int num, boolean someBoolean, List<String> args){
      System.out.println("num = [" + num + "], someBoolean = [" + someBoolean + "], args = [" + args + "]");<caret>
  }

}