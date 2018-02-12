public class S {

  {
    Map m = Map.build(<caret>).get();
  }

}

class Map {
  static Builder build(int param) {}

  static class Builder {
    Map get() {}
  }
}