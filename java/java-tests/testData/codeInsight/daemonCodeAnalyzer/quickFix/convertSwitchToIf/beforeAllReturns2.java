// "Replace 'switch' with 'if'" "true-preview"
class X {
  int m(String s, boolean r) {
    swi<caret>tch (s) {
      case "a":
        return 1;
      case "b":
        return 2;
      default:
        return 3;
    }
  }
}