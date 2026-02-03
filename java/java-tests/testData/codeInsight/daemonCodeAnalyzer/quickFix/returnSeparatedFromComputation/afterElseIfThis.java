// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    T f(int k) {
        if (k == 1)
            return new T();
        else if (k == 2)
            return null;
        return this;
    }
}