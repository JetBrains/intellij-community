// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    T f(int k) {
        T n = this;
        if (k == 1)
            n = new T();
        else if (k == 2)
            n = null;
        re<caret>turn n;
    }
}