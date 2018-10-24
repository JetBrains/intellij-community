// "Replace 'switch' with 'if'" "true"
class X {
  int m(String s, boolean r) {
    if (r) return 1;
    else
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