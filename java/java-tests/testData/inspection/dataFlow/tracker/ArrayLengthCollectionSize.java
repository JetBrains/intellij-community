/*
Value is always false (s.length == list.size())
  Left operand range is {1..Integer.MAX_VALUE} (s.length)
    Range is known from line #12 (s[0])
  Right operand range is {0} (list.size())
    Range is known from line #13 (list.isEmpty())
 */
import java.util.List;

class Test {
  void test(String[] s, List<String> list) {
    if (!s[0].isEmpty()) return;
    if (!list.isEmpty()) return;
    
    if (<selection>s.length == list.size()</selection>) {
      
    }
  }
}