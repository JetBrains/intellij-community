public final class FileAttributes {
  public static final int SYM_LINK = 0x01;
  public static final int BROKEN_SYM_LINK = 0x02;
  public static final int HIDDEN = 0x20;

  @MagicConstant(flags = {SYM_LINK, BROKEN_SYM_LINK<caret>, HIDDEN})
  public @interface Flags { }
}