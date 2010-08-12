package enums;

public enum OurEnumWithInitializedConstants {
  A(0) {
  },
  B(1) {
     void foo (){}
  },
  C(new OurBaseInterface() {}) {
     void foo () {}
  }
}
