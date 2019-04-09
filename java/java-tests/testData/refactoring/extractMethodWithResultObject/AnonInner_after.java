
class ExtractMethods { }
abstract class MyButton
        extends JButton
        {
    protected MyButton( String text ) {
        super( text );
    }
}
class Foo {
    private JButton createOKButton() {
        return new MyButton( "OK" ) {
            public void actionPerformed( int e ) {
                NewMethodResult x = newMethod();
            }

            NewMethodResult newMethod() {
                setVisible( false );
                return new NewMethodResult();
            }
        };
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}

class JButton {
    public JButton(String text) {
    }
    public void setVisible(boolean b) {}
}