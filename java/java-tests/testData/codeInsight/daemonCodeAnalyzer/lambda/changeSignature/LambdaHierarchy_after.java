class Clazz {
    public static void main(String[] args) {
        Child child = () -> System.out.println();
    }
}

interface Parent {
    void exec<caret>ute();
}

interface Child extends Parent {
    void execute();
}