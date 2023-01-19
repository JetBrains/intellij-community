// "Replace with enhanced 'switch' statement" "true"

public class EnhancedSwitchIntentionBug {
  void test() {
    outer:
    for (int i = 0; i < 10; i++) {
        switch (i) {
            case 3 -> {
                System.out.println(3);
                if (Math.random() > 0.5) break outer;
            }
            case 4 -> System.out.println(4);
        }
    }
  }
}