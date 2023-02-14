// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s, boolean r) {
    if (r)
      swi<caret>tch (s) {
        case "a":
          System.out.println("a");
        default:
          System.out.println("d");
      }
  }
}