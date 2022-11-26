// "Remove 'Exception' from 'doIt()' throws list" "false"
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;

public class Test
{
  public static class BaseClass
  {
    public void doIt() throws Exc<caret>eption
    {
    }
  }

  public static class Subclass1 extends BaseClass
  {
    @Override
    public void doIt() throws IOException
    {
      Files.createFile(Paths.get("blah"));
    }
  }

  public static class Subclass2 extends BaseClass
  {
    @Override
    public void doIt() throws ParseException
    {
      NumberFormat.getInstance().parse("x");
    }
  }
}