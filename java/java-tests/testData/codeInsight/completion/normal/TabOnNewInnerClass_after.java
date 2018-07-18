class Scratch {
  interface Some {}
  public static class Inner implements Some {}

  Some f = new Scratch.Inner()<caret>;
}