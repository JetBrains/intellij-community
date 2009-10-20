public class Main {
  Main getParent() {}

  {
    Main v;
    for (;;v.getParent()<caret>)
  }
}