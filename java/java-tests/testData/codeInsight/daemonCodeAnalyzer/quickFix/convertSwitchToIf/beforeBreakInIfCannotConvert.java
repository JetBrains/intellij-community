// "Replace 'switch' with 'if'" "false"
class X {
  void m(String s, boolean r) {
    swi<caret>tch (s) {
      case "a":
        System.out.println("a");
        if (r) {
          break;
        }
      default:
        System.out.println("d");
    }
    System.out.println("oops");
  }
}