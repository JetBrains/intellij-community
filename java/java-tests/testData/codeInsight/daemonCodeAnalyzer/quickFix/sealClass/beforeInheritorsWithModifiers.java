// "Seal class" "true"

public class Ma<caret>in { }

sealed class Direct1 extends Main {}
non-sealed class Direct2 extends Main {}
final class Direct3 extends Main {}
class Direct4 extends Main {}

class NonDirect extends Direct1 {}