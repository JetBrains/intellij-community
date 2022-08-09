// "Replace 'switch' with 'if'" "true-preview"
class Foo {
  Object foo(int x) {
    sw<caret>itch (x) {
      case 1: // not needed
        return null;
      case 2:// not needed
        return null;
      case 4:
        return "foo";
      default:
        return null;
    }
  }
}
