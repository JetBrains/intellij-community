// "Replace 'switch' with 'if'" "true-preview"
class X {
  int m(String s, boolean r) {
    switc<caret>h(s) {
      case "x":
        System.out.println("foo");
        break;
      default: {
          //ignore
        }
    }
  }
}