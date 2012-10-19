// "Simplify boolean expression" "true"
class A {
    boolean a = true;
    boolean b = true;
    boolean x = a ^ false; // intention "simplify boolean expression" does nothing here, expected: boolean x = a;
    boolean x1 = a ^ true; // expected: boolean x1 = !a; fails, does nothing
    boolean y = a ^ true ^ b;  // intention replaces with a ^ b, expected !(a ^ b) fails
    boolean y1 = a ^ <caret>false ^ b; // expected boolean y1 = a ^ b works correctly;
}