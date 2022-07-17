// "Remove unreachable branches" "true"
class Test {
    Number n = 1;
    int result = switch (n) {
      case <caret>Integer i && i == 1 -> i;
        case Long l -> l.intValue();
        case default -> 1;
    } + 10;
    {
      int i = 5;
    }
}