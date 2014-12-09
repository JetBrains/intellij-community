import static In.FOO;

// "Replace implements with static import" "true"
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
