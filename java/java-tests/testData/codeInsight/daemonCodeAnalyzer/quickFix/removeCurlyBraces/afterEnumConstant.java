// "Remove '{}'" "true-preview"

enum Planet {
  MERCURY<caret>(3.303e+23);

  final double mass; // in kilograms

  Planet(double mass) {
    this.mass = mass;
  }
}