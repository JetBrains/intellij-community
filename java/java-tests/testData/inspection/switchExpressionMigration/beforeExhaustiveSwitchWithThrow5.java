// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {

  int test5(Integer x) {
    swit<caret>ch (x) {
      case 1:
        return 2;
      case 2:
        return 4;
      default:
      case 3:
    }
    throw new IllegalArgumentException();
  }
}