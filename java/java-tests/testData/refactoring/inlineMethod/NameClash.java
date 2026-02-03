public class Foo
{
    public static void main( String[] args )
    {
        Object value = null;
        log( "float", new Float( 5.5f ) );
    }
  
    private static void <caret>log(String title, Object value) {
        System.out.println( title + ":" + value );
    }
}