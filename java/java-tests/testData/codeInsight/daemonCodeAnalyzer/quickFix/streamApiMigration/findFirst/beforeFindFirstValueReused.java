// "Replace with findFirst()" "true"

public class Test {
  public static void main(String[] args) {
    String s = "  hello  ";
    String res = s.trim();
    if(args.length == 0) {
      for (<caret>int i = 0; i < s.length(); i++) {
        Integer x = i;
        if (s.charAt(x) == 'l') {
          res = String.valueOf(x);
          break;
        }
      }
    }
    System.out.println(res);
  }
}
