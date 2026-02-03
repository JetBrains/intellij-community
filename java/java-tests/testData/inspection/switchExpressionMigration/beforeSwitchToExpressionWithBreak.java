// "Replace with enhanced 'switch' expression" "false"

class SwitchToExpressionWithBreak {
  public int test(Object o) {
    int i;
    sw<caret>itch (o) {
      case String s:
        if (s.length() == 1) {
          break;
        }
        i = 1;
        break;
      default:
        i = 2;
    }
    return i;
  }
}