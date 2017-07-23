class Map {
  static class Builder {}
  static Builder builder() {} 
}
class BiMap extends Map {
  static class Builder extends Map.Builder {}
  static Builder builder() {}
}

class Usage {
  Map.Builder b = bui<caret>
}
