import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public class Plain {
  @Nullable
  public File oper1() {
    return new File("filo");
  }

  public void oper2(int p) {
    try {
      File f = oper1();
      f.createNewFile();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    } <selection><caret>catch (NullPointerException npe) {
      npe.printStackTrace();
    }</selection> catch (Error err) {
      err.printStackTrace();
    }
  }
}