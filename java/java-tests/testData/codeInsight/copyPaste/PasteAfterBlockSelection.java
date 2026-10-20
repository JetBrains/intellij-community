public class T {
    public static void main(String[] args) {
        Object[] array = new Object[] {
            <selection>new ClassA()<caret></selection>,
            <selection>new ClassB()<caret></selection>,
        };
        
    }

    static class ClassA {}
    static class ClassB {}
}
