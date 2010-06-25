// "Do not try to create class Inner" "false"
public class Test {
    public static void main() {
        new Te<caret>st.Inner();
    }
    
    public static class Inner {}
}