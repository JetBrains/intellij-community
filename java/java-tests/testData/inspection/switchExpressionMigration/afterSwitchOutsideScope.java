// "Replace with enhanced 'switch' statement" "true-preview"

class NotDefenitessignment1 {
  void test(int x) {
    String s = "1";
    Runnable runnable = () -> {
        switch (x) {
            case 1 -> s = "2";
            case 2 -> s = "3";
        }
    };
  }
}