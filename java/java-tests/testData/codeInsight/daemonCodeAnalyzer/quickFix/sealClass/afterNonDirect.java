// "Seal class" "true"

public sealed class Main { }

non-sealed class Direct1 extends Main {}
non-sealed class Direct2 extends Main {}

class NonDirect extends Direct1 {}