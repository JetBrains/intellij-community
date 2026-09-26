/*
Value is always false (s.equalsIgnoreCase("xyz"); line#12)
  According to hard-coded contract, method 'equalsIgnoreCase' returns 'false' when length of s != length of "xyz" (equalsIgnoreCase; line#12)
    Left operand is in {4..Integer.MAX_VALUE} (s; line#12)
      Range is known from line #10 (s.substring(0, 4); line#10)
    and right operand is 3 ("xyz"; line#12)
 */
public class SubStringEqualsIgnoreCase {
  void test(String s) {
    if (s.substring(0, 4).equals("abcd") || s.equalsIgnoreCase("1234")) {

    } else if (<selection>s.equalsIgnoreCase("xyz")</selection>) {

    }
  }
}