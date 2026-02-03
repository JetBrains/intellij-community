// "Replace with enhanced 'switch' statement" "true-preview"

class NotDefenitessignment1 {
  void test(int x) {
    String s = "1";
    Runnable runnable = () -> {
      switc<caret>h (x) {
        case 1:
          s = "2";
          break;
        case 2:
          s = "3";
          break;
      }
    };
  }
}