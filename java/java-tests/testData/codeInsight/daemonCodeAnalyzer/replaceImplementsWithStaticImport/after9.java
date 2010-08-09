import static In.FOO;

// "Replace Implements with Static Import" "true"
interface In {
    int FOO = 0;
}

class II {
    public static void main(String[] args) {
        System.out.println(FOO);
    }
}

class II1 {
    int FOO = 9;
    public static void main(String[] args) {
        System.out.println(In.FOO);
    }
}
