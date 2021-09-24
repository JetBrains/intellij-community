
class Main {
  public static abstract sealed class Sealed {
    public static final class SealedInheritor extends Sealed {}
  }

  int f(Sealed o) {
    return switch(o) {
        case Sealed.SealedInheritor, null
    }
  }

  int g(Sealed o) {
    return switch(o) {
        case null, Sealed.SealedInheritor
    }
  }

  int h(Sealed o) {
    return switch(o) {
        case Sealed.SealedInheritor
    }
  }
}