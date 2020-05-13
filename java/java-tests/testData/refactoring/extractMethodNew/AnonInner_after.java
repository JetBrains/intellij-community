
public class ExtractMethods { }
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
                newMethod();
            }

            private void newMethod() {
                setVisible( false );
            }
        };
    }
}

class JButton {
    public JButton(String text) {
    }
    public void setVisible(boolean b) {}
}