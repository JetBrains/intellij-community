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
    public static void main(String[] args) {
        System.out.println(FOO);
    }
}

class Uc {
    static final String FOO = "";
    void g() {
        System.out.println("" + In.FOO);
    }
}
