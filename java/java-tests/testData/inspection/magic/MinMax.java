import org.intellij.lang.annotations.MagicConstant;

class MinMax {
  static final int PRIVATE = 1;
  static final int PROTECTED = 2;
  static final int PUBLIC = 3;

  @MagicConstant(valuesFromClass = MinMax.class)
  @interface Access {}

  void process(@Access int access, int unknown) {
    @Access int a1 = Math.max(access, PROTECTED);
    @Access int a2 = Math.min(PUBLIC, access);
    @Access int a3 = Math.max(access, (Math.min(PRIVATE, PUBLIC)));
    @Access int a4 = <warning descr="Should be one of: MinMax.PRIVATE, MinMax.PROTECTED, MinMax.PUBLIC">Math.max(access, unknown)</warning>;
    @Access int a5 = <warning descr="Should be one of: MinMax.PRIVATE, MinMax.PROTECTED, MinMax.PUBLIC">Math.max(access, 5)</warning>;
  }
}

class LongValues {
  static final long L1 = 1L;
  static final long L2 = 2L;

  @MagicConstant(valuesFromClass = LongValues.class)
  @interface LongValue {}

  void process(@LongValue long l) {
    @LongValue long l1 = Math.max(l, L2);
    @LongValue long l2 = <warning descr="Should be one of: LongValues.L1, LongValues.L2">Math.min(l, 5L)</warning>;
  }
}

class Flags {
  static final int FLAG1 = 1;
  static final int FLAG2 = 2;

  @MagicConstant(flagsFromClass = Flags.class)
  @interface Flag {}

  void process(@Flag int flags) {
    @Flag int f1 = <warning descr="Should be one of: Flags.FLAG1, Flags.FLAG2 or their combination">Math.max(flags, FLAG1)</warning>;
  }
}