/*
Value is always false (s.length == list.size(); line#15)
  Left operand is >= 1 (s.length; line#15)
    Range is known from line #12 (s[0]; line#12)
  and right operand is 0 (list.size(); line#15)
    Range is known from line #13 (!list.isEmpty(); line#13)
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