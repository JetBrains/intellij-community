class C {
    void <caret>method(int... args) {
    }

    {
        method(1,2);
        method(1,2,3);
        method();
    }
}