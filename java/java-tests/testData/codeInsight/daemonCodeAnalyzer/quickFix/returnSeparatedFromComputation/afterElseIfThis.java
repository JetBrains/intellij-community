// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    T f(int k) {
        if (k == 1)
            return new T();
        else if (k == 2)
            return null;
        return this;
    }
}