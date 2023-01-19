// "Remove unreachable branches" "true"
class Test {
    void test(EmptyBox box) {
        switch (box) {
            case Empt<caret>yBox() emptyBox when true -> {
                System.out.println(emptyBox);
            }
        }
    }

    record EmptyBox() {}
}
