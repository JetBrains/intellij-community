// "Fix the typo 'breal' to 'break'" "true-preview"
public class Test {
  public static void main(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      break;
    }
  }
}