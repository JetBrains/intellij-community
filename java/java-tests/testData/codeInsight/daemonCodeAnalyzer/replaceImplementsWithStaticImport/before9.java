// "Replace Implements with Static Import" "true"
interface I<caret>n {
    int FOO = 0;
}

class II implements In {
    public static void main(String[] args) {
        System.out.println(FOO);
    }
}

class II1 implements In {
    int FOO = 9;
    public static void main(String[] args) {
        System.out.println(In.FOO);
    }
}
