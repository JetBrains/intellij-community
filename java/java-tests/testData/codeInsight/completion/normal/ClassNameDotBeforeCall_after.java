import java.io.FileInputStream;

public class Foo {
  {
      verify(runP4WithClient("integ", "main/...", "rel/..."));
      FileInputStream.<caret>
      verify(runP4WithClient("resolve", "-at"));
  }
}