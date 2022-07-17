// "Remove unreachable branches" "true"
class Test {
    Number n = 1;
    int result;
    {
        Integer i1 = (Integer) n;
        result = i1 + 10;
        int i = 5;
    }
}