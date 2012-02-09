public class Foo {
  {
      verify(runP4WithClient("integ", "main/...", "rel/..."));
      FInpSt.<caret>
      verify(runP4WithClient("resolve", "-at"));
  }
}