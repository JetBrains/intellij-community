// "Replace 'switch' with 'if'" "true"
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
  }
}