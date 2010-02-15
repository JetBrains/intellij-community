public class VisibilityPinline {
    private static DifferentScope cashedObject = new DifferentScope();

    private static DifferentScope provideObject() {
        return new DifferentScope();
    }

    public void context() {
        DifferentScope vB = new DifferentScope();
        vB.inlineB(provideObject());
    }
}

class DifferentScope {
    private int value = 1;

    public void mutate() {
        value++;
    }

    public void inlineB(DifferentScope <caret>subj) {
        subj.mutate();
    }
}
