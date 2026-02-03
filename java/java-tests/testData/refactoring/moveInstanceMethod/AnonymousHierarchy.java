public class Test {

    public boolean per<caret>form(Bar l) {
        final Dialog dialog = new Dialog() {
                    protected void invokeRefactoring() {
                         doRefactor();
                    }

                };
        dialog.show();
        return dialog.isOK();
    }
}

class Bar {
}

class ID {
    void doRefactor() {

    }
}

class Dialog extends ID {
    public void show() {
    }

    public boolean isOK() {
        return false;
    }
}