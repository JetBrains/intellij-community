// "Remove unreachable branches" "true"
class Test {
    void test(EmptyBox box) {
        switch (box) {
            case Empty<caret>Box() emptyBox when true -> {
                System.out.println("Fill it up and send it back");
            }
        }
    }

    record EmptyBox() {}
}
