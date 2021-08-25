class Clazz {
    public static void main(String[] args) {
        Child child = x -> System.out.println();
    }
}

interface Parent {
    void exec<caret>ute(int i);
}

interface Child extends Parent {
    void execute(int i);
}