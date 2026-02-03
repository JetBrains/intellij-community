public class Test {
    private void prepare<caret>AnonymousClasses(JetElement aClass) {
        aClass.acceptChildren(new JetVisitor() {
            public void visitJetElement(JetElement element) {
                element.acceptChildren(this);
            }
        });
    }
}


class JetElement {
    void acceptChildren(JetVisitor v) {}
}

class JetVisitor {}