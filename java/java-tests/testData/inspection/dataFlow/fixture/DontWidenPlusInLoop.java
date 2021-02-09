public class DontWidenPlusInLoop {
  public static int parse(String value, int i) {
    do {
      i = value.indexOf('{', i);
      if (i != -1 && i == value.indexOf("{{", i)) {
        i += 2;
      }
      else {
        break;
      }
    }
    while (<warning descr="Condition 'i != -1' is always 'true'">i != -1</warning>);
    return i;
  }
}