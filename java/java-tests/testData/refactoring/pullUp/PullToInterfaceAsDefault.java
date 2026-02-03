interface IPlanet {}

class SimplePlanet implements IPlanet {
  double mass;

  private double ma<caret>ss() {
    return mass;
  }
}