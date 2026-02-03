import java.nio.MappedByteBuffer;

class Test
{
  public static int triggerBug(MappedByteBuffer buffer)
  {
    int a = buffer.getInt();
    int b = buffer.getInt();
    if(a == 0 && b == 1)
      return 0;
    else
      return 1;
  }
}