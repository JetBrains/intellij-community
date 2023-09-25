// "Replace with 'switch' expression" "true-preview"

class X {
  enum State {
    CANCELLED, INTERRUPTING, NORMAL
  }

  String test(State state, String outcome) {
    sw<caret>itch (state){
      default:
        break;
      case CANCELLED:
      case INTERRUPTED:
        return "2";
      }
    return "1";
  }
}