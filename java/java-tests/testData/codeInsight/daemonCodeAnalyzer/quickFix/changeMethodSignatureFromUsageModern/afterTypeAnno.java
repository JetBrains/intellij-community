// "Add 'String' as 2nd parameter to method 'f'" "true-preview"
import java.util.List;
 public class S {
     void f(List<@Anno String> args, String s) {
     f(null, "");
     }
 }
