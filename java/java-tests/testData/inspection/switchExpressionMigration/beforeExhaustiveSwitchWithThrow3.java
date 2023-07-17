// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {
  int test3(int x) {
    swit<caret>ch (x) {
      case 1:
        return 2;
      case 2:
        return 4;
      case 3:
      default:
    }
    throw new IllegalArgumentException();
  }
}