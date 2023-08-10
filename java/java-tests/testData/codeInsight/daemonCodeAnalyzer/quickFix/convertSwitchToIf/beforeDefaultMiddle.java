// "Replace 'switch' with 'if'" "true-preview"
class X {
  void m(String s) {
    swi<caret>tch (s) {
      case "foo":
        System.out.println(1);
        break;
      default:
        System.out.println(2);
        break;
      case "bar":
        System.out.println(3);
    }
  }
}