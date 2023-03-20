// "Remove unreachable branches" "true"
class Test {
    void test(EmptyBox box) {
        System.out.println(box);
    }

    record EmptyBox() {}
}
