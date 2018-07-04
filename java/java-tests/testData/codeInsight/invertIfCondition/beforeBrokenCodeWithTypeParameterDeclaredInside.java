// "Invert 'if' condition" "true"
class A {
    public void annotate() {
        <caret>if (equals(2)) return;
        annotation.registerFix(new IntentionAction() {
            public void invoke() throws IncorrectOperationException {
            }
        });
        annotation.registerFix(new IntentionAction() {
            public <IncorrectOperationException> void invoke() throws IncorrectOperationException {
            }

        });
    }
}