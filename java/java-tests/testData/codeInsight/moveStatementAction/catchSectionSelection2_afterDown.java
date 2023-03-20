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
    } catch (Throwable t) {
      t.printStackTrace();
    } cat<selection><caret>ch (NullPointerException npe) {
      npe.printStackTrace();
    } cat</selection>ch (Error err) {
      err.printStackTrace();
    }
  }
}