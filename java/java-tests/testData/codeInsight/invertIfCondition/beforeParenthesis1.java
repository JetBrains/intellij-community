// "Invert 'if' condition" "true"
class Inversion {
        public void context(boolean a, boolean b, boolean c) {
                if (a &<caret>& b || c) {
                        System.out.println(0);
                } else {
                        System.out.println(1);
                }
        }
} 