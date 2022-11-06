// "Remove unreachable branches" "true"
class Test {
    void test(EmptyBox box) {
        System.out.println("Fill it up and send it back");
    }

    record EmptyBox() {}
}
