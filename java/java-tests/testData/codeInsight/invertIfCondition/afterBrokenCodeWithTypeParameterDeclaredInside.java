// "Invert 'if' condition" "true"
class A {
    public void annotate() {
        if (!equals(2)) {
            annotation.registerFix(new IntentionAction() {
                public void invoke() throws IncorrectOperationException {
                }
            });
            annotation.registerFix(new IntentionAction() {
                public <IncorrectOperationException> void invoke() throws IncorrectOperationException {
                }

            });
        }
        else {
            return;
        }
    }
}