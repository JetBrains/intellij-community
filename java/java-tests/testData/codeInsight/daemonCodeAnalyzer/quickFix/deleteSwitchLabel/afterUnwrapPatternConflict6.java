// "Remove unreachable branches" "true"
class Test {
    Number n = 1;
    Integer i = (Integer) n;
    int result = i + 10;
    {
      int i = 5;
    }
}