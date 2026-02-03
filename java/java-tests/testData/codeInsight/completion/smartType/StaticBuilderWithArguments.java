public class S {

  {
    Map m = bui<caret>
  }

}

class Map {
  static Builder build(int param) {}

  static class Builder {
    Map get() {}
  }
}