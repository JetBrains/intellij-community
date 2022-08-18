// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s, boolean r) {
    swi<caret>tch (s) {
      case "a":
        System.out.println("a");
        if (r) {
          break;
        } else {
          throw new RuntimeException();
        }
      default:
        System.out.println("d");
    }
  }
}