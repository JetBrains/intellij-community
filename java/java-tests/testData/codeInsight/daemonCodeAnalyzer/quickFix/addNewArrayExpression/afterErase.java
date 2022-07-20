// "Add 'new List[]'" "true-preview"
import java.util.List;
class c {
 void f(List<String> l) {
   List<String>[] a;
   a = new List[]{l};
 }
}