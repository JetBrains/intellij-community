// "Replace 'switch' with 'if'" "true"
class X {
  int m(int i) {
    if (i > 0)
      sw<caret>itch (++i) {
        case 1:
          System.out.println(1);
        case 2:
          System.out.println(2);
      }
  }
}