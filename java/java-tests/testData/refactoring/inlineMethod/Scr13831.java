public class Foo
{
    public static void main( String[] args )
    {
        log( "integer", new Integer( 5 ) );
        log( "float", new Float( 5.5f ) );
    }
  
    private static void <caret>log(String title, Object value) {
        System.out.println( title + ":" + value );
    }
}