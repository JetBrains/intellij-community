// "Transform body to single exit-point form" "true-preview"
class Test {
    void <caret>test(String s) {
        if (s != null) {
            int a;
            synchronized (this) {
                if (s.isEmpty()) return;
                a = s.length();
            }
            System.out.println(a);
        }
    }
}