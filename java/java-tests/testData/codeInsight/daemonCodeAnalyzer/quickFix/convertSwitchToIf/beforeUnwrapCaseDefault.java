// "Replace 'switch' with 'if'" "true-preview"
public class One {
  void f1(String a) {
    sw<caret>itch (a) {
      case "one":
        System.out.println(1);
      case null, default:
        System.out.println("default");
    }
  }
}