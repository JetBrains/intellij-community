// "Do not try to create class Inner" "false"
public class Test {
    public static void main() {
        new Inner<caret>();
    }
    
    public static class Inner {}
}