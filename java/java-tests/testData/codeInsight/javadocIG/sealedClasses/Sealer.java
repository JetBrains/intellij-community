public abstract sealed class Sealer permits Sealer.Sealed, SecondSealer {
    private static non-sealed class Sealed extends Sealer {}
}