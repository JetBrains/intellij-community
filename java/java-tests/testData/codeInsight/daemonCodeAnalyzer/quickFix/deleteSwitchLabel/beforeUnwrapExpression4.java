// "Remove unreachable branches" "false"
class X {
    int x(E e) {
        // We cannot do anything here (yet)
        return switch (E.AA) {
            case A<caret>A: 
                System.out.println(9);
            default: break 0;
        };
    }
}
enum E {
    AA,BB
}