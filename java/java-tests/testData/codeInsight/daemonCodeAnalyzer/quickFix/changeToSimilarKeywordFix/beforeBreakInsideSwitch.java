// "Fix the typo 'breal' to 'break'" "true-preview"
public class Test {
  public static void main(String[] args) {
    switch (args.length) {
      case 0:
        breal<caret>
    }
  }
}