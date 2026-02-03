import org.jetbrains.annotations.Contract;

class Zoo {
  @Contract( "null->null" )
  static byte[] bar( String s )
  {
    if ( s == null )
      return null;
    return new byte[0];
  }}