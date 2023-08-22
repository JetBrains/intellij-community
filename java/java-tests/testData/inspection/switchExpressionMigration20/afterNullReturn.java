// "Replace with enhanced 'switch' statement" "true"

class A {
  private int void testSwitch2(Integer c) {
        switch (c) {
            case null -> {
                return 1;
            }
            case -1 -> {
                return 1;
            }
            case 0 -> {
                return 0;
            }
            case 1, 2 -> {
                return 3;
            }
            default -> {
                return 5;
            }
        }
  }
}