// "Seal class" "true"

public sealed class Main { }

sealed class Direct1 extends Main {}
non-sealed class Direct2 extends Main {}
final class Direct3 extends Main {}
non-sealed class Direct4 extends Main {}

class NonDirect extends Direct1 {}